package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.attempt.service.ExamAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('LE','AD')")
public class ExamAttemptController {
    private final ExamAttemptService service;

    public ExamAttemptController(ExamAttemptService service) { this.service = service; }

    @GetMapping("/exam-sessions/{examId}/attempt-settings")
    public ResponseEntity<ApiResponse<Object>> getSettings(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Attempt settings retrieved", service.getSettings(examId)));
    }

    @PutMapping("/exam-sessions/{examId}/attempt-settings")
    public ResponseEntity<ApiResponse<Object>> updateSettings(@PathVariable UUID examId,
            @Valid @RequestBody AttemptSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attempt settings updated", service.updateSettings(examId,
                new ExamAttemptService.SettingsRequest(request.maxAttempts(), request.scorePolicy(),
                        request.avoidRepeatQuestions(), request.cooldownMinutes(), request.attemptWindowStart(),
                        request.attemptWindowEnd(), request.allowLecturerExtraAttempts(), request.countAbandoned(),
                        request.countExpired(), request.version()))));
    }

    @GetMapping("/exam-sessions/{examId}/students/{studentId}/attempts")
    public ResponseEntity<ApiResponse<Object>> studentAttempts(@PathVariable UUID examId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("Student attempts retrieved", service.studentAttempts(examId, studentId)));
    }

    @PostMapping("/exam-sessions/{examId}/students/{studentId}/attempts")
    public ResponseEntity<ApiResponse<Object>> createAttempt(@PathVariable UUID examId, @PathVariable UUID studentId,
            @Valid @RequestBody CreateAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Scheduled attempt created",
                service.createScheduledAttempt(examId, studentId, request.scheduledTime())));
    }

    @PostMapping("/exam-sessions/{examId}/students/{studentId}/grant-attempts")
    public ResponseEntity<ApiResponse<Object>> grant(@PathVariable UUID examId, @PathVariable UUID studentId,
            @Valid @RequestBody AttemptAllowanceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Extra attempts granted",
                service.grant(examId, studentId, request.additional(), request.reason())));
    }

    @PostMapping("/exam-sessions/{examId}/students/{studentId}/revoke-attempts")
    public ResponseEntity<ApiResponse<Object>> revoke(@PathVariable UUID examId, @PathVariable UUID studentId,
            @Valid @RequestBody AttemptAllowanceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unused extra attempts revoked",
                service.revoke(examId, studentId, request.additional(), request.reason())));
    }

    @PutMapping("/exam-attempts/{attemptId}/schedule")
    public ResponseEntity<ApiResponse<Object>> schedule(@PathVariable UUID attemptId,
            @Valid @RequestBody ScheduleAttemptRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attempt scheduled",
                service.schedule(attemptId, request.scheduledTime(), request.slotOrder())));
    }

    @PostMapping("/exam-attempts/{attemptId}/cancel")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable UUID attemptId,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unused attempt cancelled", service.cancel(attemptId, request.reason())));
    }

    @PostMapping("/exam-attempts/{attemptId}/void")
    public ResponseEntity<ApiResponse<Object>> voidAttempt(@PathVariable UUID attemptId,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attempt voided", service.voidAttempt(attemptId, request.reason())));
    }

    @PutMapping("/exam-sessions/{examId}/students/{studentId}/official-attempt")
    public ResponseEntity<ApiResponse<Object>> chooseOfficial(@PathVariable UUID examId, @PathVariable UUID studentId,
            @Valid @RequestBody OfficialAttemptRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Official result selected",
                service.chooseOfficialAttempt(examId, studentId, request.attemptId(), request.confirm())));
    }

    @GetMapping("/exam-sessions/{examId}/attempts/audit")
    public ResponseEntity<ApiResponse<Object>> audit(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Attempt audit retrieved", service.audit(examId)));
    }

    @GetMapping("/exam-sessions/{examId}/official-results")
    public ResponseEntity<ApiResponse<Object>> officialResults(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Official exam results retrieved", service.officialResults(examId)));
    }

    public record AttemptSettingsRequest(@Min(1) int maxAttempts, @NotBlank String scorePolicy,
            boolean avoidRepeatQuestions, @Min(0) int cooldownMinutes,
            OffsetDateTime attemptWindowStart, OffsetDateTime attemptWindowEnd,
            boolean allowLecturerExtraAttempts, boolean countAbandoned, boolean countExpired, long version) { }
    public record CreateAttemptRequest(@NotNull OffsetDateTime scheduledTime) { }
    public record ScheduleAttemptRequest(@NotNull OffsetDateTime scheduledTime, Integer slotOrder) { }
    public record AttemptAllowanceRequest(@Min(1) int additional, @NotBlank String reason) { }
    public record ReasonRequest(@NotBlank String reason) { }
    public record OfficialAttemptRequest(@NotNull UUID attemptId, boolean confirm) { }
}
