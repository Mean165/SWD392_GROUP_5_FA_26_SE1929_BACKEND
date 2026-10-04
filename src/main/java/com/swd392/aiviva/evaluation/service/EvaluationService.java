package com.swd392.aiviva.evaluation.service;

import com.swd392.aiviva.evaluation.dto.request.EvaluationRequest;
import com.swd392.aiviva.evaluation.dto.response.EvaluationResponse;

public interface EvaluationService {

    EvaluationResponse evaluate(EvaluationRequest request);
}

