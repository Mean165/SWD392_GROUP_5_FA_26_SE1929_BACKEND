package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.entity.ExamSessionBloomDistribution;
import com.swd392.aiviva.exam.repository.ExamSessionBloomDistributionRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.AttemptQuestionPlan;
import com.swd392.aiviva.interview.entity.InteractionTurn;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.AttemptQuestionPlanRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.service.RubricResolver;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionPlanService {
    private final AttemptQuestionPlanRepository planRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamSessionBloomDistributionRepository distributionRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final QuestionRepository questionRepository;
    private final InteractionTurnRepository turnRepository;
    private final VivaAttemptRepository attemptRepository;
    private final RubricResolver rubricResolver;

    public QuestionPlanService(AttemptQuestionPlanRepository planRepository,
                               ExamSessionRepository sessionRepository,
                               ExamSessionBloomDistributionRepository distributionRepository,
                               StudentSessionAssignmentRepository assignmentRepository,
                               QuestionRepository questionRepository,
                               InteractionTurnRepository turnRepository,
                               RubricResolver rubricResolver,
                               VivaAttemptRepository attemptRepository) {
        this.planRepository = planRepository;
        this.sessionRepository = sessionRepository;
        this.distributionRepository = distributionRepository;
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.turnRepository = turnRepository;
        this.rubricResolver = rubricResolver;
        this.attemptRepository = attemptRepository;
    }

    @Transactional
    public List<AttemptQuestionPlan> generatePlan(VivaAttempt attempt) {
        List<AttemptQuestionPlan> existing = planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attempt.getAttemptId());
        if (!existing.isEmpty()) return existing;
        ExamSession session = sessionRepository.findByIdForUpdate(attempt.getAssignment().getExamSession().getSessionId())
                .orElseThrow(() -> new ConflictException("Exam session is unavailable while generating the question plan."));
        if (session.getTopic() == null) throw new ConflictException("Exam session has no topic configured.");

        Map<BloomLevel, Integer> requested = distribution(session);
        int total = requested.values().stream().mapToInt(Integer::intValue).sum();
        if (total != session.getMaxMainQuestions()) {
            throw new ConflictException("Bloom distribution must total max_main_questions.");
        }
        List<Question> selected = new ArrayList<>();
        List<BloomLevel> selectedLevels = new ArrayList<>();
        Set<UUID> previousWindow = previousWindowQuestionIds(attempt.getAssignment());
        Map<UUID, Integer> usage = usageByQuestion(session.getSessionId());
        Random random = new Random(attempt.getAttemptId().getMostSignificantBits() ^ attempt.getAttemptId().getLeastSignificantBits());
        List<String> relaxations = new ArrayList<>();

        for (BloomLevel level : BloomLevel.values()) {
            int count = requested.getOrDefault(level, 0);
            for (int i = 0; i < count; i++) {
                List<Question> candidates = candidates(session, level, selected, previousWindow);
                if (candidates.isEmpty()) {
                    relaxations.add("avoid-repeat:" + level.name());
                    candidates = candidatesWithoutWindow(session, level, selected);
                }
                if (candidates.isEmpty()) {
                    relaxations.add("adjacent-bloom:" + level.name());
                    candidates = adjacentCandidates(session, level, selected);
                }
                if (candidates.isEmpty()) {
                    throw new ConflictException("Question bank is insufficient for Bloom level " + level.name()
                            + "; required " + count + " question(s), still missing " + (count - i), "QUESTION_PLAN_INSUFFICIENT");
                }
                candidates.sort(Comparator.comparingInt(q -> usage.getOrDefault(q.getQuestionId(), 0)));
                int lowest = usage.getOrDefault(candidates.get(0).getQuestionId(), 0);
                List<Question> tied = candidates.stream().filter(q -> usage.getOrDefault(q.getQuestionId(), 0) == lowest).toList();
                Question chosen = tied.get(random.nextInt(tied.size()));
                selected.add(chosen);
                selectedLevels.add(BloomLevel.parse(chosen.getBloomLevel()));
                usage.merge(chosen.getQuestionId(), 1, Integer::sum);
            }
        }

        // Preserve questions already asked by legacy attempts at their earliest positions.
        List<Question> legacy = turnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attempt.getAttemptId()).stream()
                .filter(t -> "MAIN".equalsIgnoreCase(t.getTurnType()) && t.getMainQuestion() != null)
                .map(InteractionTurn::getMainQuestion).distinct().toList();
        for (int i = 0; i < Math.min(legacy.size(), selected.size()); i++) {
            Question old = legacy.get(i);
            int found = selected.indexOf(old);
            if (found > i) {
                Question swap = selected.get(i); selected.set(i, old); selected.set(found, swap);
                BloomLevel level = selectedLevels.get(i); selectedLevels.set(i, BloomLevel.parse(old.getBloomLevel())); selectedLevels.set(found, level);
            }
        }
        List<AttemptQuestionPlan> plans = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            plans.add(AttemptQuestionPlan.builder().attempt(attempt).question(selected.get(i)).position(i + 1)
                    .bloomLevel(selectedLevels.get(i)).createdAt(OffsetDateTime.now()).build());
        }
        return planRepository.saveAll(plans);
    }

    @Transactional(readOnly = true)
    public List<AttemptQuestionPlan> getPlan(UUID attemptId) {
        return planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attemptId);
    }

    private Map<BloomLevel, Integer> distribution(ExamSession session) {
        List<ExamSessionBloomDistribution> rows = distributionRepository.findBySession_SessionId(session.getSessionId());
        Map<BloomLevel, Integer> result = new EnumMap<>(BloomLevel.class);
        if (!rows.isEmpty()) {
            rows.forEach(row -> result.put(row.getBloomLevel(), row.getQuestionCount()));
            return result;
        }
        BloomLevel[] defaults = switch (session.getMaxMainQuestions()) {
            case 1 -> new BloomLevel[]{BloomLevel.UNDERSTAND};
            case 2 -> new BloomLevel[]{BloomLevel.UNDERSTAND, BloomLevel.APPLY};
            case 3 -> new BloomLevel[]{BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE};
            case 4 -> new BloomLevel[]{BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE, BloomLevel.EVALUATE};
            case 5 -> new BloomLevel[]{BloomLevel.REMEMBER, BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE, BloomLevel.EVALUATE};
            default -> Arrays.copyOf(BloomLevel.values(), Math.min(session.getMaxMainQuestions(), BloomLevel.values().length));
        };
        for (int i = 0; i < session.getMaxMainQuestions(); i++) result.merge(defaults[i % defaults.length], 1, Integer::sum);
        return result;
    }

    private List<Question> candidates(ExamSession session, BloomLevel level, List<Question> selected, Set<UUID> previous) {
        return baseCandidates(session, level).stream().filter(q -> !previous.contains(q.getQuestionId()))
                .filter(q -> selected.stream().noneMatch(s -> s.getQuestionId().equals(q.getQuestionId()))).collect(Collectors.toCollection(ArrayList::new));
    }
    private List<Question> candidatesWithoutWindow(ExamSession session, BloomLevel level, List<Question> selected) {
        return baseCandidates(session, level).stream().filter(q -> selected.stream().noneMatch(s -> s.getQuestionId().equals(q.getQuestionId()))).collect(Collectors.toCollection(ArrayList::new));
    }
    private List<Question> adjacentCandidates(ExamSession session, BloomLevel level, List<Question> selected) {
        List<BloomLevel> nearby = Arrays.stream(BloomLevel.values()).filter(l -> Math.abs(l.getOrder() - level.getOrder()) == 1).toList();
        return nearby.stream().flatMap(l -> candidatesWithoutWindow(session, l, selected).stream()).collect(Collectors.toCollection(ArrayList::new));
    }
    private List<Question> baseCandidates(ExamSession session, BloomLevel level) {
        return questionRepository.findByTopic_TopicIdAndApprovalStatusOrderByCreatedAtAsc(session.getTopic().getTopicId(), "APPROVED").stream()
                .filter(q -> level.name().equalsIgnoreCase(q.getBloomLevel())).filter(this::hasRubric).toList();
    }
    private boolean hasRubric(Question q) { try { rubricResolver.resolveForQuestion(q.getQuestionId()); return true; } catch (RuntimeException ex) { return false; } }
    private Map<UUID, Integer> usageByQuestion(UUID sessionId) {
        Map<UUID, Integer> usage = new HashMap<>();
        assignmentRepository.findByExamSessionSessionId(sessionId).forEach(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId())
                .ifPresent(attempt -> planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attempt.getAttemptId())
                        .forEach(plan -> usage.merge(plan.getQuestion().getQuestionId(), 1, Integer::sum))));
        return usage;
    }
    private Set<UUID> previousWindowQuestionIds(StudentSessionAssignment assignment) {
        int window = assignment.getExamSession().getAvoidRepeatWindow() == null ? 2 : assignment.getExamSession().getAvoidRepeatWindow();
        if (window <= 0) return Set.of();
        List<StudentSessionAssignment> previous;
        if (assignment.getSlotOrder() != null) {
            previous = assignmentRepository.findPreviousAssignments(assignment.getExamSession().getSessionId(), assignment.getSlotOrder(), org.springframework.data.domain.PageRequest.of(0, window));
        } else if (assignment.getScheduledTime() != null) {
            previous = assignmentRepository.findByExamSessionSessionId(assignment.getExamSession().getSessionId()).stream()
                    .filter(a -> a.getScheduledTime() != null && a.getScheduledTime().isBefore(assignment.getScheduledTime()))
                    .sorted(Comparator.comparing(StudentSessionAssignment::getScheduledTime).reversed()).limit(window).toList();
        } else {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        previous.forEach(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).ifPresent(attempt ->
                planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attempt.getAttemptId()).forEach(plan -> ids.add(plan.getQuestion().getQuestionId()))));
        return ids;
    }
}
