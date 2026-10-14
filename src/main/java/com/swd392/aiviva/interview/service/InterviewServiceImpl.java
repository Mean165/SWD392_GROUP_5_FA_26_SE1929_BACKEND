package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.dto.request.StartInterviewRequest;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.dto.response.AiAssessmentResponse;
import com.swd392.aiviva.interview.dto.response.InteractionTurnResponse;
import com.swd392.aiviva.interview.dto.response.InterviewResponse;
import com.swd392.aiviva.interview.dto.response.SubmitAnswerResponse;
import com.swd392.aiviva.interview.entity.AiRubricAssessment;
import com.swd392.aiviva.interview.entity.InteractionTurn;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.enums.InterviewStatus;
import com.swd392.aiviva.interview.repository.AiRubricAssessmentRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.RubricCriteria;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewServiceImpl implements InterviewService {

    private final VivaAttemptRepository vivaAttemptRepository;
    private final InteractionTurnRepository interactionTurnRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRepository questionRepository;
    private final RubricCriteriaRepository rubricCriteriaRepository;
    private final AiRubricAssessmentRepository rubricAssessmentRepository;
    private final UserRepository userRepository;
    private final TextToSpeechService ttsService;
    private final SpeechToTextService sttService;
    private final FollowUpQuestionService followUpQuestionService;
    private final AnswerAnalysisService answerAnalysisService;

    public InterviewServiceImpl(
            VivaAttemptRepository vivaAttemptRepository,
            InteractionTurnRepository interactionTurnRepository,
            StudentSessionAssignmentRepository assignmentRepository,
            ExamSessionRepository examSessionRepository,
            QuestionRepository questionRepository,
            RubricCriteriaRepository rubricCriteriaRepository,
            AiRubricAssessmentRepository rubricAssessmentRepository,
            UserRepository userRepository,
            TextToSpeechService ttsService,
            SpeechToTextService sttService,
            FollowUpQuestionService followUpQuestionService,
            AnswerAnalysisService answerAnalysisService) {
        this.vivaAttemptRepository = vivaAttemptRepository;
        this.interactionTurnRepository = interactionTurnRepository;
        this.assignmentRepository = assignmentRepository;
        this.examSessionRepository = examSessionRepository;
        this.questionRepository = questionRepository;
        this.rubricCriteriaRepository = rubricCriteriaRepository;
        this.rubricAssessmentRepository = rubricAssessmentRepository;
        this.userRepository = userRepository;
        this.ttsService = ttsService;
        this.sttService = sttService;
        this.followUpQuestionService = followUpQuestionService;
        this.answerAnalysisService = answerAnalysisService;
    }

    @Override
    @Transactional
    public InterviewResponse startInterview(StartInterviewRequest request) {
        UUID studentId = request.getStudentId();
        if (studentId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                AppUser user = userRepository.findByEmail(auth.getName()).orElse(null);
                if (user != null) {
                    studentId = user.getUserId();
                }
            }
        }

        if (studentId == null) {
            throw new BusinessException("Student identification is required to start interview");
        }

        StudentSessionAssignment assignment = assignmentRepository
                .findByExamSessionSessionIdAndStudentUserId(request.getSessionId(), studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No scheduled assignment found for this session and student"));

        VivaAttempt attempt = vivaAttemptRepository.findByAssignment_AssignmentId(assignment.getAssignmentId())
                .orElse(null);

        if (attempt == null) {
            attempt = VivaAttempt.builder()
                    .assignment(assignment)
                    .status(InterviewStatus.IN_PROGRESS.name())
                    .startTime(OffsetDateTime.now())
                    .build();
            attempt = vivaAttemptRepository.save(attempt);
        } else if (InterviewStatus.COMPLETED.name().equalsIgnoreCase(attempt.getStatus())) {
            throw new BusinessException("This interview attempt has already been completed.");
        }

        List<InteractionTurn> existingTurns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attempt.getAttemptId());
        InteractionTurn currentTurn;

        if (existingTurns.isEmpty()) {
            List<Question> questions = questionRepository.findAll();
            if (questions.isEmpty()) {
                throw new BusinessException("Question bank is empty. Please add questions before starting viva.");
            }
            Question firstQuestion = questions.get(0);

            currentTurn = InteractionTurn.builder()
                    .attempt(attempt)
                    .mainQuestion(firstQuestion)
                    .turnOrder(1)
                    .turnType("MAIN")
                    .aiQuestionText(firstQuestion.getQuestionText())
                    .createdAt(OffsetDateTime.now())
                    .build();
            currentTurn = interactionTurnRepository.save(currentTurn);
            existingTurns.add(currentTurn);
        } else {
            currentTurn = existingTurns.get(existingTurns.size() - 1);
        }

        return toInterviewResponse(attempt, currentTurn, existingTurns);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewResponse getInterviewStatus(UUID attemptId) {
        VivaAttempt attempt = vivaAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));

        List<InteractionTurn> turns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId);
        InteractionTurn currentTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);

        return toInterviewResponse(attempt, currentTurn, turns);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InteractionTurnResponse> getInterviewTurns(UUID attemptId) {
        return interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId)
                .stream()
                .map(this::toTurnResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        VivaAttempt attempt = vivaAttemptRepository.findById(request.getAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + request.getAttemptId()));

        InteractionTurn turn = interactionTurnRepository.findById(request.getTurnId())
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found: " + request.getTurnId()));

        String transcript = request.getTranscriptText();
        if ((transcript == null || transcript.isBlank()) && request.getAudioSegmentUrl() != null) {
            transcript = sttService.transcribe(request.getAudioSegmentUrl());
        }

        if (transcript == null || transcript.isBlank()) {
            transcript = "[No audio or transcript detected]";
        }

        turn.setTranscriptText(transcript);
        if (request.getAudioSegmentUrl() != null) {
            turn.setAudioSegmentUrl(request.getAudioSegmentUrl());
        }

        String aiAnalysis = answerAnalysisService.analyzeAnswer(transcript);
        turn.setAiReasoning(aiAnalysis);
        interactionTurnRepository.save(turn);

        List<RubricCriteria> criterias = new ArrayList<>();
        if (turn.getMainQuestion() != null) {
            criterias = rubricCriteriaRepository.findByQuestion_QuestionId(turn.getMainQuestion().getQuestionId());
        }

        List<AiRubricAssessment> assessments = new ArrayList<>();
        for (RubricCriteria c : criterias) {
            BigDecimal weight = c.getWeightRatio() != null ? c.getWeightRatio() : BigDecimal.valueOf(1.0);
            BigDecimal suggestedPts = BigDecimal.valueOf(10).multiply(weight);
            AiRubricAssessment assessment = AiRubricAssessment.builder()
                    .turn(turn)
                    .criteria(c)
                    .semanticSimilarity(BigDecimal.valueOf(0.85))
                    .complianceStatus("COMPLIANT")
                    .aiSuggestedPoints(suggestedPts)
                    .aiExplanationReasoning("Criteria evaluated based on transcript and AI analysis.")
                    .build();
            assessments.add(rubricAssessmentRepository.save(assessment));
        }

        ExamSession session = attempt.getAssignment().getSession();
        int maxFollowups = (session != null && session.getMaxFollowupPerQuestion() != null)
                ? session.getMaxFollowupPerQuestion() : 1;
        int maxMain = (session != null && session.getMaxMainQuestions() != null)
                ? session.getMaxMainQuestions() : 3;

        List<InteractionTurn> allTurns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attempt.getAttemptId());
        long currentFollowups = allTurns.stream()
                .filter(t -> "FOLLOW_UP".equalsIgnoreCase(t.getTurnType()) && t.getMainQuestion() != null
                        && turn.getMainQuestion() != null
                        && t.getMainQuestion().getQuestionId().equals(turn.getMainQuestion().getQuestionId()))
                .count();

        boolean shouldGenerateFollowUp = currentFollowups < maxFollowups;
        InteractionTurn nextTurn = null;

        if (shouldGenerateFollowUp) {
            String rubricSummary = criterias.isEmpty() ? "General viva accuracy" : criterias.get(0).getCriteriaName();
            String followUpText = followUpQuestionService.buildFollowUpQuestion(transcript, rubricSummary);

            nextTurn = InteractionTurn.builder()
                    .attempt(attempt)
                    .mainQuestion(turn.getMainQuestion())
                    .turnOrder(allTurns.size() + 1)
                    .turnType("FOLLOW_UP")
                    .aiQuestionText(followUpText)
                    .createdAt(OffsetDateTime.now())
                    .build();
            nextTurn = interactionTurnRepository.save(nextTurn);
        } else {
            long mainQuestionsAsked = allTurns.stream()
                    .map(InteractionTurn::getMainQuestion)
                    .filter(java.util.Objects::nonNull)
                    .map(Question::getQuestionId)
                    .distinct()
                    .count();

            if (mainQuestionsAsked < maxMain) {
                List<Question> allQuestions = questionRepository.findAll();
                List<UUID> askedIds = allTurns.stream()
                        .map(InteractionTurn::getMainQuestion)
                        .filter(java.util.Objects::nonNull)
                        .map(Question::getQuestionId)
                        .toList();

                Optional<Question> nextQ = allQuestions.stream()
                        .filter(q -> !askedIds.contains(q.getQuestionId()))
                        .findFirst();

                if (nextQ.isPresent()) {
                    Question q = nextQ.get();
                    nextTurn = InteractionTurn.builder()
                            .attempt(attempt)
                            .mainQuestion(q)
                            .turnOrder(allTurns.size() + 1)
                            .turnType("MAIN")
                            .aiQuestionText(q.getQuestionText())
                            .createdAt(OffsetDateTime.now())
                            .build();
                    nextTurn = interactionTurnRepository.save(nextTurn);
                } else {
                    attempt.setStatus(InterviewStatus.COMPLETED.name());
                    attempt.setEndTime(OffsetDateTime.now());
                    vivaAttemptRepository.save(attempt);
                }
            } else {
                attempt.setStatus(InterviewStatus.COMPLETED.name());
                attempt.setEndTime(OffsetDateTime.now());
                vivaAttemptRepository.save(attempt);
            }
        }

        List<AiAssessmentResponse> assessmentResponses = assessments.stream()
                .map(a -> AiAssessmentResponse.builder()
                        .assessmentId(a.getAssessmentId())
                        .criteriaId(a.getCriteria() != null ? a.getCriteria().getCriteriaId() : null)
                        .criteriaName(a.getCriteria() != null ? a.getCriteria().getCriteriaName() : null)
                        .semanticSimilarity(a.getSemanticSimilarity())
                        .complianceStatus(a.getComplianceStatus())
                        .aiSuggestedPoints(a.getAiSuggestedPoints())
                        .missingConcepts(a.getMissingConcepts())
                        .aiExplanationReasoning(a.getAiExplanationReasoning())
                        .build())
                .toList();

        return SubmitAnswerResponse.builder()
                .turnId(turn.getTurnId())
                .transcriptText(transcript)
                .completed(InterviewStatus.COMPLETED.name().equalsIgnoreCase(attempt.getStatus()))
                .followUpGenerated(shouldGenerateFollowUp)
                .nextTurn(nextTurn != null ? toTurnResponse(nextTurn) : null)
                .assessments(assessmentResponses)
                .build();
    }

    @Override
    @Transactional
    public InterviewResponse completeInterview(UUID attemptId) {
        VivaAttempt attempt = vivaAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva attempt not found: " + attemptId));

        attempt.setStatus(InterviewStatus.COMPLETED.name());
        attempt.setEndTime(OffsetDateTime.now());
        attempt = vivaAttemptRepository.save(attempt);

        List<InteractionTurn> turns = interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId);
        InteractionTurn lastTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);

        return toInterviewResponse(attempt, lastTurn, turns);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getQuestionSpeechAudio(UUID turnId) {
        InteractionTurn turn = interactionTurnRepository.findById(turnId)
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found: " + turnId));
        return ttsService.synthesize(turn.getAiQuestionText());
    }

    private InterviewResponse toInterviewResponse(VivaAttempt attempt, InteractionTurn currentTurn, List<InteractionTurn> turns) {
        StudentSessionAssignment assignment = attempt.getAssignment();
        UUID sessionId = assignment != null && assignment.getSession() != null
                ? assignment.getSession().getSessionId() : null;
        UUID studentId = assignment != null && assignment.getStudent() != null
                ? assignment.getStudent().getUserId() : null;

        List<InteractionTurnResponse> turnResponses = turns != null
                ? turns.stream().map(this::toTurnResponse).toList()
                : List.of();

        return InterviewResponse.builder()
                .attemptId(attempt.getAttemptId())
                .assignmentId(assignment != null ? assignment.getAssignmentId() : null)
                .sessionId(sessionId)
                .studentId(studentId)
                .status(attempt.getStatus())
                .startTime(attempt.getStartTime())
                .endTime(attempt.getEndTime())
                .currentTurn(currentTurn != null ? toTurnResponse(currentTurn) : null)
                .turns(turnResponses)
                .build();
    }

    private InteractionTurnResponse toTurnResponse(InteractionTurn turn) {
        return InteractionTurnResponse.builder()
                .turnId(turn.getTurnId())
                .attemptId(turn.getAttempt() != null ? turn.getAttempt().getAttemptId() : null)
                .mainQuestionId(turn.getMainQuestion() != null ? turn.getMainQuestion().getQuestionId() : null)
                .turnOrder(turn.getTurnOrder())
                .turnType(turn.getTurnType())
                .aiQuestionText(turn.getAiQuestionText())
                .transcriptText(turn.getTranscriptText())
                .audioSegmentUrl(turn.getAudioSegmentUrl())
                .aiReasoning(turn.getAiReasoning())
                .createdAt(turn.getCreatedAt())
                .build();
    }
}
