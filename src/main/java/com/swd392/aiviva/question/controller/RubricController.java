package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricResponse;
import com.swd392.aiviva.question.service.RubricService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rubrics")
public class RubricController {

    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RubricResponse>> createRubric(@Valid @RequestBody RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rubric created", rubricService.createRubric(request)));
    }
}

