package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.QuestionGenerationRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.service.QuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestions() {
        return ResponseEntity.ok(ApiResponse.success("Questions retrieved", questionService.getQuestions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestionById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Question retrieved", questionService.getQuestionById(id)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<QuestionResponse>> generateQuestion(@Valid @RequestBody QuestionGenerationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question generation requested", questionService.generateQuestion(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable UUID id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok(ApiResponse.success("Question deleted successfully", null));
    }
}
