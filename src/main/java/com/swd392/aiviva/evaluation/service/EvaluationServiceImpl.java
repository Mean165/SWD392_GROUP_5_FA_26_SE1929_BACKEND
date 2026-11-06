package com.swd392.aiviva.evaluation.service;

import com.swd392.aiviva.evaluation.dto.request.EvaluationRequest;
import com.swd392.aiviva.evaluation.dto.response.EvaluationResponse;
import com.swd392.aiviva.question.service.RubricResolver;
import com.swd392.aiviva.evaluation.enums.EvaluationStatus;
import org.springframework.stereotype.Service;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final RubricResolver rubricResolver;

    public EvaluationServiceImpl(RubricResolver rubricResolver) {
        this.rubricResolver = rubricResolver;
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        if (request.getQuestionId() != null) {
            rubricResolver.resolveForQuestion(request.getQuestionId());
        }
        return new EvaluationResponse(null, request.getInterviewId(), request.getStudentId(), EvaluationStatus.AI_SUGGESTED);
    }
}

