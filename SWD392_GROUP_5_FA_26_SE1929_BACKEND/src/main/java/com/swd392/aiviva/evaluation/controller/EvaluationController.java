package com.swd392.aiviva.evaluation.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.evaluation.dto.request.EvaluationRequest;
import com.swd392.aiviva.evaluation.dto.response.EvaluationResponse;
import com.swd392.aiviva.evaluation.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EvaluationResponse>> createEvaluation(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Evaluation created", evaluationService.evaluate(request)));
    }
}

