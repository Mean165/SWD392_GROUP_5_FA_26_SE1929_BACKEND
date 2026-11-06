package com.swd392.aiviva.question.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.Rubric;
import com.swd392.aiviva.question.entity.RubricCriteria;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.question.repository.RubricRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricResolver {
    private final QuestionRepository questionRepository;
    private final RubricCriteriaRepository criteriaRepository;
    private final RubricRepository rubricRepository;

    public RubricResolver(QuestionRepository questionRepository,
            RubricCriteriaRepository criteriaRepository, RubricRepository rubricRepository) {
        this.questionRepository = questionRepository;
        this.criteriaRepository = criteriaRepository;
        this.rubricRepository = rubricRepository;
    }

    @Transactional(readOnly = true)
    public List<RubricCriteria> resolveForQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));
        UUID subjectId = question.getTopic() == null || question.getTopic().getSubject() == null
                ? null : question.getTopic().getSubject().getSubjectId();
        if (subjectId == null) throw new BusinessException("Question topic is not assigned to a subject.");
        return resolveForSubject(subjectId).criteria();
    }

    @Transactional(readOnly = true)
    public ResolvedRubric resolveForSubject(UUID subjectId) {
        Rubric rubric = activeRubric(subjectId).orElseThrow(() -> new BusinessException(
                "Subject " + subjectId + " is missing an active rubric."));
        List<RubricCriteria> criteria = criteriaRepository.findByRubric_RubricIdOrderByCriteriaId(rubric.getRubricId());
        if (!hasValidCriteria(criteria)) throw new BusinessException(
                "Active subject rubric " + rubric.getRubricId() + " has invalid criteria or weights.");
        return new ResolvedRubric(rubric, criteria);
    }

    @Transactional(readOnly = true)
    public boolean hasValidRubric(UUID subjectId) {
        return activeRubric(subjectId).map(rubric -> hasValidCriteria(
                criteriaRepository.findByRubric_RubricIdOrderByCriteriaId(rubric.getRubricId()))).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Rubric> activeRubric(UUID subjectId) {
        return rubricRepository.findFirstBySubject_SubjectIdAndStatusOrderByUpdatedAtDesc(subjectId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public Resolution resolveWithSource(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));
        UUID subjectId = question.getTopic() == null || question.getTopic().getSubject() == null
                ? null : question.getTopic().getSubject().getSubjectId();
        if (subjectId == null) throw new BusinessException("Question topic is not assigned to a subject.");
        ResolvedRubric resolved = resolveForSubject(subjectId);
        return new Resolution("SUBJECT", resolved.rubric(), resolved.criteria());
    }

    private boolean hasValidCriteria(List<RubricCriteria> criteria) {
        if (criteria == null || criteria.isEmpty()) return false;
        BigDecimal total = BigDecimal.ZERO;
        for (RubricCriteria criterion : criteria) {
            BigDecimal weight = criterion.getWeightRatio();
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(BigDecimal.ONE) > 0) {
                return false;
            }
            total = total.add(weight);
        }
        return total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.001")) <= 0;
    }

    public record ResolvedRubric(Rubric rubric, List<RubricCriteria> criteria) { }
    public record Resolution(String source, Rubric rubric, List<RubricCriteria> criteria) { }
}
