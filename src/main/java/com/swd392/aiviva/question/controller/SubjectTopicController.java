package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.CreateSubjectTopicRequest;
import com.swd392.aiviva.question.dto.response.SubjectTopicResponse;
import com.swd392.aiviva.question.service.SubjectTopicService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class SubjectTopicController {

    private final SubjectTopicService topicService;

    public SubjectTopicController(SubjectTopicService topicService) {
        this.topicService = topicService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubjectTopicResponse>> createTopic(
            @Valid @RequestBody CreateSubjectTopicRequest request,
            Principal principal) {
        
        SubjectTopicResponse response = topicService.createTopic(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Topic created successfully", response));
    }

    @GetMapping("/{topicId}")
    public ResponseEntity<ApiResponse<SubjectTopicResponse>> getTopicById(
            @PathVariable UUID topicId) {
        
        SubjectTopicResponse response = topicService.getTopicById(topicId);
        return ResponseEntity.ok(ApiResponse.success("Topic retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<SubjectTopicResponse>>> getAllTopics() {
        return ResponseEntity.ok(ApiResponse.success("Topics retrieved successfully", topicService.getAllTopics()));
    }
}
