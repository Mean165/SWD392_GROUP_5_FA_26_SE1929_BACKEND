package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.dto.request.AssignStudentSessionRequest;
import com.swd392.aiviva.exam.dto.request.BulkAssignStudentsRequest;
import com.swd392.aiviva.exam.dto.request.CancelExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.ScheduleExamRequest;
import com.swd392.aiviva.exam.dto.request.UpdateAssignmentScheduleRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.dto.response.ExamReadinessResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.service.ExamSessionService;
import com.swd392.aiviva.common.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<PageResponse<ExamSessionResponse>>> getAllExamSessions(
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) java.time.OffsetDateTime from, @RequestParam(required = false) java.time.OffsetDateTime to,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Exam sessions retrieved successfully", examSessionService.search(status, topicId, from, to, keyword, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
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

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Exam session started", examSessionService.startSession(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelExamSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Exam session cancelled", examSessionService.cancelSession(id, request)));
    }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<ExamReadinessResponse>> readiness(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Exam readiness retrieved", examSessionService.readiness(id)));
    }

    @PostMapping("/{id}/students/bulk")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<java.util.Map<String,Object>>> bulkAssign(@PathVariable UUID id, @Valid @RequestBody BulkAssignStudentsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Students assigned", examSessionService.bulkAssign(id, request)));
    }

    @PostMapping(value = "/{id}/students/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<com.swd392.aiviva.exam.service.StudentImportResult>> importStudents(@PathVariable UUID id, @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Students imported", examSessionService.importStudents(id, file)));
    }

    @PostMapping("/{id}/schedule/auto")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<java.util.Map<String,Object>>> autoSchedule(@PathVariable UUID id, @RequestBody ScheduleExamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Exam schedule generated", examSessionService.autoSchedule(id, request)));
    }

    @PutMapping("/{sessionId}/students/{studentId}/schedule")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<StudentSessionAssignmentResponse>> updateSchedule(@PathVariable UUID sessionId, @PathVariable UUID studentId, @Valid @RequestBody UpdateAssignmentScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Assignment schedule updated", examSessionService.updateAssignmentSchedule(sessionId, studentId, request)));
    }

    @DeleteMapping("/{sessionId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<Void>> removeStudent(@PathVariable UUID sessionId, @PathVariable UUID studentId) {
        examSessionService.removeStudent(sessionId, studentId); return ResponseEntity.ok(ApiResponse.success("Student removed", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) { examSessionService.deleteSession(id); return ResponseEntity.ok(ApiResponse.success("Exam session deleted", null)); }

    @GetMapping("/{sessionId}/students")
    @PreAuthorize("hasAnyRole('AD', 'LE')")
    public ResponseEntity<ApiResponse<PageResponse<StudentSessionAssignmentResponse>>> getStudentsBySessionId(
            @PathVariable("sessionId") UUID sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Assigned students retrieved successfully",
                examSessionService.getAssignmentsBySessionId(sessionId, status, page, size)));
    }
}
