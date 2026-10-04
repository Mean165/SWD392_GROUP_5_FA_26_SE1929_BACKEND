package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.QuestionCreateRequest;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.request.QuestionUpdateRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.service.QuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestions(
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) String bloomLevel) {
        return ResponseEntity.ok(ApiResponse.success("Questions retrieved", questionService.getQuestions(topicId, bloomLevel)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestionById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Question retrieved", questionService.getQuestionById(id)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<QuestionResponse>> generateQuestion(@Valid @RequestBody QuestionGenerationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question generation requested", questionService.generateQuestion(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(@Valid @RequestBody QuestionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question created successfully", questionService.createQuestion(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question updated successfully", questionService.updateQuestion(id, request)));
    }
}
