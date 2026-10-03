package com.swd392.aiviva.evaluation.service;

import com.swd392.aiviva.evaluation.dto.request.EvaluationRequest;
import com.swd392.aiviva.evaluation.dto.response.EvaluationResponse;
import org.springframework.stereotype.Service;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        // TODO: Evaluate transcript and remarks, then keep lecturer final decision separate.
        return null;
    }
}

