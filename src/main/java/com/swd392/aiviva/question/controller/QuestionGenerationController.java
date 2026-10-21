package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.BulkApproveRequest;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.EditQuestionRequest;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import com.swd392.aiviva.question.application.QuestionWorkflowService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/question-generation")
public class QuestionGenerationController {
    private final QuestionWorkflowService service;

    public QuestionGenerationController(QuestionWorkflowService service) { this.service = service; }

    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<?>> subjects() {
        return ResponseEntity.ok(ApiResponse.success("Assigned subjects", service.subjects()));
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public ResponseEntity<ApiResponse<?>> topics(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success("Subject topics", service.topics(subjectId)));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<?>> documents(@RequestParam UUID subjectId, @RequestParam UUID topicId) {
        return ResponseEntity.ok(ApiResponse.success("Lecturer documents", service.documents(subjectId, topicId)));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> upload(@RequestParam UUID subjectId, @RequestParam UUID topicId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Document accepted for processing", service.upload(subjectId, topicId, file)));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<?>> document(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success("Document status", service.document(documentId)));
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID documentId) {
        service.deleteDocument(documentId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<?>> createJob(@Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Generation job queued", service.createJob(request)));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<?>> job(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success("Generation job status", service.job(jobId)));
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<?>> questions(@RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) UUID documentId, @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID topicId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String bloomLevel, @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean bankOnly) {
        Map<String, Object> result = service.listQuestions(jobId, documentId, subjectId, topicId, status, bloomLevel, sourceType, page, size, bankOnly);
        return ResponseEntity.ok(ApiResponse.success("Questions retrieved", result));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<?>> question(@PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.success("Question details", service.question(questionId)));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<?>> edit(@PathVariable UUID questionId, @Valid @RequestBody EditQuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question updated", service.editQuestion(questionId, request)));
    }

    @PostMapping("/questions/{questionId}/approve")
    public ResponseEntity<ApiResponse<?>> approve(@PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.success("Question approved", service.approve(questionId)));
    }

    @PostMapping("/questions/approve-bulk")
    public ResponseEntity<ApiResponse<?>> approveMany(@Valid @RequestBody BulkApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Questions approved", service.approveMany(request)));
    }

    @PostMapping("/questions/{questionId}/reject")
    public ResponseEntity<ApiResponse<?>> reject(@PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.success("Question rejected", service.reject(questionId)));
    }
}
