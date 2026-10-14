package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.CreateSubjectTopicRequest;
import com.swd392.aiviva.question.dto.response.QuestionResponse;
import com.swd392.aiviva.question.dto.response.SubjectTopicResponse;
import com.swd392.aiviva.question.service.QuestionService;
import com.swd392.aiviva.question.service.SubjectTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Topic Management", description = "Quản lý chủ đề (Topic) và câu hỏi theo chủ đề")
@RestController
@RequestMapping("/api/topics")
public class SubjectTopicController {

    private final SubjectTopicService topicService;
    private final QuestionService questionService;

    public SubjectTopicController(SubjectTopicService topicService, QuestionService questionService) {
        this.topicService = topicService;
        this.questionService = questionService;
    }

    @Operation(summary = "Tạo mới một chủ đề (Topic)")
    @PostMapping
    public ResponseEntity<ApiResponse<SubjectTopicResponse>> createTopic(
            @Valid @RequestBody CreateSubjectTopicRequest request,
            Principal principal) {
        
        SubjectTopicResponse response = topicService.createTopic(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Topic created successfully", response));
    }

    @Operation(summary = "Lấy chi tiết chủ đề theo Topic ID")
    @GetMapping("/{topicId}")
    public ResponseEntity<ApiResponse<SubjectTopicResponse>> getTopicById(
            @Parameter(description = "ID của chủ đề (UUID)", required = true)
            @PathVariable UUID topicId) {
        
        SubjectTopicResponse response = topicService.getTopicById(topicId);
        return ResponseEntity.ok(ApiResponse.success("Topic retrieved successfully", response));
    }

    @Operation(summary = "Lấy danh sách tất cả chủ đề")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubjectTopicResponse>>> getAllTopics() {
        return ResponseEntity.ok(ApiResponse.success("Topics retrieved successfully", topicService.getAllTopics()));
    }

    @Operation(
            summary = "Lấy danh sách câu hỏi theo Topic ID",
            description = "API dành cho Frontend: Truyền topicId để lấy toàn bộ danh sách các câu hỏi thuộc chủ đề đó."
    )
    @GetMapping("/{topicId}/questions")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestionsByTopicId(
            @Parameter(description = "ID của chủ đề (Topic ID - UUID)", required = true, example = "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e")
            @PathVariable UUID topicId) {
        List<QuestionResponse> questions = questionService.getQuestionsByTopicId(topicId);
        return ResponseEntity.ok(ApiResponse.success("Questions retrieved successfully for topic", questions));
    }
}
