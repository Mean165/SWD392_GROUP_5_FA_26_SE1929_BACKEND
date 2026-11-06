package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.exam.dto.response.ExamEntryEligibilityResponse;
import com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse;
import com.swd392.aiviva.exam.service.StudentExamAssignmentService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/exam-assignments")
public class StudentExamAssignmentController {

    private final StudentExamAssignmentService assignmentService;

    public StudentExamAssignmentController(StudentExamAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StudentExamAssignmentResponse>>> getSchedule(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Exam schedule retrieved", assignmentService.getSchedule(from, to, status, page, size)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<StudentExamAssignmentResponse>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Exam history retrieved", assignmentService.getHistory(page, size)));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<ApiResponse<StudentExamAssignmentResponse>> getAssignment(
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Exam assignment retrieved", assignmentService.getAssignment(assignmentId)));
    }

    @GetMapping("/{assignmentId}/eligibility")
    public ResponseEntity<ApiResponse<ExamEntryEligibilityResponse>> getEligibility(
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Exam entry eligibility retrieved", assignmentService.getEligibility(assignmentId)));
    }
}
