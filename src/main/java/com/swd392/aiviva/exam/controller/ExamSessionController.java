package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.dto.request.AssignStudentSessionRequest;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.service.ExamSessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exam-sessions")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    public ExamSessionController(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> createExamSession(
            @Valid @RequestBody CreateExamSessionRequest request) {
        ExamSessionResponse response = examSessionService.createExamSession(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Exam session created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> updateExamSession(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateExamSessionRequest request) {
        ExamSessionResponse response = examSessionService.updateExamSession(id, request);
        return ResponseEntity.ok(ApiResponse.success("Exam session updated successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> getAllExamSessions() {
        return ResponseEntity.ok(ApiResponse.success("Exam sessions retrieved successfully", examSessionService.getAllExamSessions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> getExamSessionById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Exam session retrieved successfully", examSessionService.getExamSessionById(id)));
    }

    @PostMapping("/assign-student")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<StudentSessionAssignmentResponse>> assignStudent(
            @Valid @RequestBody AssignStudentSessionRequest request) {
        StudentSessionAssignmentResponse response = examSessionService.assignStudentToSession(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student assigned to exam session successfully", response));
    }

    @PostMapping("/{sessionId}/assign-student")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<StudentSessionAssignmentResponse>> assignStudentToSession(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody AssignStudentSessionRequest request) {
        StudentSessionAssignmentResponse response = examSessionService.assignStudentToSession(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student assigned to exam session successfully", response));
    }

    @GetMapping("/{sessionId}/students")
    public ResponseEntity<ApiResponse<List<StudentSessionAssignmentResponse>>> getStudentsBySessionId(
            @PathVariable("sessionId") UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Assigned students retrieved successfully",
                examSessionService.getAssignmentsBySessionId(sessionId)));
    }
}
