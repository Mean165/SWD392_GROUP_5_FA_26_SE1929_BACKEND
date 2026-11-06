package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.interview.repository.AiRubricAssessmentRepository;
import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricMatrixResponse;
import com.swd392.aiviva.question.dto.response.RubricResolutionResponse;
import com.swd392.aiviva.question.dto.response.RubricResponse;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.Rubric;
import com.swd392.aiviva.question.entity.RubricCriteria;
import com.swd392.aiviva.question.entity.Subject;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.question.repository.RubricRepository;
import com.swd392.aiviva.question.repository.SubjectRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricServiceImpl implements RubricService {
    private final RubricRepository rubricRepository;
    private final RubricCriteriaRepository criteriaRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final AiRubricAssessmentRepository assessmentRepository;
    private final RubricResolver resolver;

    public RubricServiceImpl(RubricRepository rubricRepository, RubricCriteriaRepository criteriaRepository,
            SubjectRepository subjectRepository, SubjectTopicRepository topicRepository,
            QuestionRepository questionRepository, UserRepository userRepository,
            AiRubricAssessmentRepository assessmentRepository, RubricResolver resolver) {
        this.rubricRepository = rubricRepository;
        this.criteriaRepository = criteriaRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.assessmentRepository = assessmentRepository;
        this.resolver = resolver;
    }

    @Override
    @Transactional
    public RubricResponse createRubric(UUID subjectId, RubricRequest request) {
        Subject subject = subject(subjectId);
        authorizeSubject(subject);
        if (rubricRepository.existsBySubject_SubjectIdAndStatus(subjectId, "ACTIVE")) {
            throw new ConflictException("An ACTIVE rubric already exists for this subject.", "ACTIVE_SUBJECT_RUBRIC_EXISTS");
        }
        validateWeights(request.getCriteria());
        OffsetDateTime now = OffsetDateTime.now();
        Rubric rubric = rubricRepository.save(Rubric.builder().subject(subject).name(request.getName().trim())
                .description(request.getDescription()).status("ACTIVE").createdBy(currentUser())
                .createdAt(now).updatedAt(now).build());
        saveCriteria(rubric, request.getCriteria());
        return toResponse(rubric);
    }

    @Override
    @Transactional
    public RubricResponse createRubricForTopic(UUID topicId, RubricRequest request) {
        SubjectTopic topic = topic(topicId);
        if (topic.getSubject() == null) throw new BusinessException("Topic is not assigned to a subject.");
        return createRubric(topic.getSubject().getSubjectId(), request);
    }

    @Override
    @Transactional(readOnly = true)
    public RubricResponse getActiveRubric(UUID subjectId) {
        Subject subject = subject(subjectId);
        authorizeSubject(subject);
        Rubric rubric = resolver.activeRubric(subjectId).orElseThrow(() ->
                new ResourceNotFoundException("Active rubric not found for subject: " + subjectId));
        return toResponse(rubric);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RubricResponse> listRubrics(UUID subjectId, String status) {
        Subject subject = subject(subjectId);
        authorizeSubject(subject);
        String normalized = normalizeStatus(status);
        List<Rubric> rubrics = normalized == null ? rubricRepository.findBySubject_SubjectIdOrderByUpdatedAtDesc(subjectId)
                : rubricRepository.findBySubject_SubjectIdAndStatusOrderByUpdatedAtDesc(subjectId, normalized);
        return rubrics.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RubricResponse> listRubricsForTopic(UUID topicId, String status) {
        SubjectTopic topic = topic(topicId);
        if (topic.getSubject() == null) return List.of();
        return listRubrics(topic.getSubject().getSubjectId(), status);
    }

    @Override
    @Transactional(readOnly = true)
    public RubricResponse getRubric(UUID rubricId) {
        Rubric rubric = rubric(rubricId);
        authorizeSubject(rubric.getSubject());
        return toResponse(rubric);
    }

    @Override
    @Transactional(readOnly = true)
    public RubricMatrixResponse questionStats(UUID subjectId) {
        Subject subject = subject(subjectId);
        authorizeSubject(subject);
        List<Question> approved = questionRepository
                .findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(subjectId, "APPROVED");
        Map<String, Long> byBloom = new java.util.LinkedHashMap<>();
        for (BloomLevel level : BloomLevel.values()) {
            byBloom.put(level.name(), approved.stream().filter(q -> level.name().equals(q.getBloomLevel())).count());
        }
        Rubric active = resolver.activeRubric(subjectId).orElse(null);
        long criteriaCount = active == null ? 0 : criteriaRepository.countByRubric_RubricId(active.getRubricId());
        return RubricMatrixResponse.builder().subjectId(subjectId)
                .activeRubricId(active == null ? null : active.getRubricId())
                .criteriaCount(criteriaCount).approvedQuestionsByBloom(byBloom).build();
    }

    @Override
    @Transactional(readOnly = true)
    public RubricMatrixResponse questionStatsForTopic(UUID topicId) {
        SubjectTopic topic = topic(topicId);
        if (topic.getSubject() == null) throw new BusinessException("Topic is not assigned to a subject.");
        return questionStats(topic.getSubject().getSubjectId());
    }

    @Override
    @Transactional
    public RubricResponse updateRubric(UUID rubricId, RubricRequest request) {
        Rubric old = rubric(rubricId);
        authorizeSubject(old.getSubject());
        validateWeights(request.getCriteria());
        List<RubricCriteria> existing = criteriaRepository.findByRubric_RubricIdOrderByCriteriaId(rubricId);
        boolean used = existing.stream().anyMatch(c -> assessmentRepository.existsByCriteria_CriteriaId(c.getCriteriaId()));
        OffsetDateTime now = OffsetDateTime.now();
        if (used) {
            old.setStatus("ARCHIVED");
            old.setUpdatedAt(now);
            rubricRepository.save(old);
            Rubric version = rubricRepository.save(Rubric.builder().subject(old.getSubject())
                    .name(request.getName().trim()).description(request.getDescription()).status("ACTIVE")
                    .createdBy(currentUser()).createdAt(now).updatedAt(now).build());
            saveCriteria(version, request.getCriteria());
            return toResponse(version);
        }
        old.setName(request.getName().trim());
        old.setDescription(request.getDescription());
        old.setUpdatedAt(now);
        rubricRepository.save(old);
        criteriaRepository.deleteAll(existing);
        saveCriteria(old, request.getCriteria());
        return toResponse(old);
    }

    @Override
    @Transactional
    public RubricResponse updateActiveRubric(UUID subjectId, RubricRequest request) {
        Rubric active = resolver.activeRubric(subjectId).orElseThrow(() ->
                new ResourceNotFoundException("Active rubric not found for subject: " + subjectId));
        return updateRubric(active.getRubricId(), request);
    }

    @Override
    @Transactional
    public void deleteRubric(UUID rubricId) {
        Rubric rubric = rubric(rubricId);
        authorizeSubject(rubric.getSubject());
        boolean used = criteriaRepository.findByRubric_RubricIdOrderByCriteriaId(rubricId).stream()
                .anyMatch(c -> assessmentRepository.existsByCriteria_CriteriaId(c.getCriteriaId()));
        if (used) {
            rubric.setStatus("ARCHIVED");
            rubric.setUpdatedAt(OffsetDateTime.now());
            rubricRepository.save(rubric);
        } else {
            rubricRepository.delete(rubric);
        }
    }

    @Override
    @Transactional
    public void deleteActiveRubric(UUID subjectId) {
        Rubric active = resolver.activeRubric(subjectId).orElseThrow(() ->
                new ResourceNotFoundException("Active rubric not found for subject: " + subjectId));
        deleteRubric(active.getRubricId());
    }

    @Override
    @Transactional(readOnly = true)
    public RubricResolutionResponse getEffectiveRubric(UUID questionId) {
        RubricResolver.Resolution resolved = resolver.resolveWithSource(questionId);
        Question question = questionRepository.findById(questionId).orElseThrow();
        return toResolution(BloomLevel.parse(question.getBloomLevel()).name(), resolved);
    }

    @Override
    @Transactional
    public RubricResolutionResponse setQuestionOverride(UUID questionId, RubricRequest request) {
        throw new BusinessException("Question-specific rubrics are no longer supported; update the subject rubric.");
    }

    private void saveCriteria(Rubric rubric, List<RubricRequest.CriterionRequest> inputs) {
        criteriaRepository.saveAll(inputs.stream().map(input -> RubricCriteria.builder().rubric(rubric)
                .criteriaName(input.getCriteriaName().trim())
                .expectedKnowledgePoints(input.getExpectedKnowledgePoints().trim())
                .weightRatio(input.getWeightRatio().setScale(2, RoundingMode.HALF_UP)).build()).toList());
    }

    private void validateWeights(List<RubricRequest.CriterionRequest> criteria) {
        if (criteria == null || criteria.isEmpty()) throw new BusinessException("Rubric must contain at least one criterion.");
        BigDecimal total = BigDecimal.ZERO;
        for (RubricRequest.CriterionRequest criterion : criteria) {
            BigDecimal weight = criterion.getWeightRatio();
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException("Each weight ratio must be greater than 0 and at most 1.");
            }
            if (weight.scale() > 2) throw new BusinessException("Weight ratio supports at most two decimal places.");
            total = total.add(weight);
        }
        if (total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) > 0) {
            throw new BusinessException("Rubric weight ratios must total 1.00.");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "ARCHIVED").contains(normalized)) throw new BusinessException("status must be ACTIVE or ARCHIVED.");
        return normalized;
    }

    private Subject subject(UUID id) {
        return subjectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + id));
    }

    private SubjectTopic topic(UUID id) {
        return topicRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject topic not found: " + id));
    }

    private Rubric rubric(UUID id) {
        return rubricRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rubric not found: " + id));
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found."));
    }

    private void authorizeSubject(Subject subject) {
        AppUser user = currentUser();
        String role = user.getRole() == null ? "" : user.getRole().getRoleCode();
        if ("AD".equalsIgnoreCase(role)) return;
        if (!"LE".equalsIgnoreCase(role) || subject == null
                || !subjectRepository.isLecturerAssigned(subject.getSubjectId(), user.getUserId())) {
            throw new AccessDeniedException("You can only manage rubrics for subjects assigned to you.");
        }
    }

    private RubricResponse toResponse(Rubric rubric) {
        return RubricResponse.builder().rubricId(rubric.getRubricId())
                .subjectId(rubric.getSubject() == null ? null : rubric.getSubject().getSubjectId())
                .name(rubric.getName()).description(rubric.getDescription()).status(rubric.getStatus())
                .createdBy(rubric.getCreatedBy() == null ? null : rubric.getCreatedBy().getUserId())
                .createdAt(rubric.getCreatedAt()).updatedAt(rubric.getUpdatedAt()).source("SUBJECT")
                .criteria(criteriaRepository.findByRubric_RubricIdOrderByCriteriaId(rubric.getRubricId())
                        .stream().map(this::criterionResponse).toList()).build();
    }

    private RubricResponse.CriterionResponse criterionResponse(RubricCriteria criterion) {
        return RubricResponse.CriterionResponse.builder().criteriaId(criterion.getCriteriaId())
                .rubricId(criterion.getRubric() == null ? null : criterion.getRubric().getRubricId())
                .questionId(criterion.getQuestion() == null ? null : criterion.getQuestion().getQuestionId())
                .criteriaName(criterion.getCriteriaName()).expectedKnowledgePoints(criterion.getExpectedKnowledgePoints())
                .weightRatio(criterion.getWeightRatio()).build();
    }

    private RubricResolutionResponse toResolution(String bloom, RubricResolver.Resolution resolved) {
        return RubricResolutionResponse.builder().source(resolved.source()).bloomLevel(bloom)
                .rubricId(resolved.rubric().getRubricId())
                .criteria(resolved.criteria().stream().map(this::criterionResponse).toList()).build();
    }
}
