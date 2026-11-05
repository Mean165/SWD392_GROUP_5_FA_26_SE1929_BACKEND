package com.swd392.aiviva.evaluation.grading.web;

import com.swd392.aiviva.auth.service.CurrentUserProvider;
import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.evaluation.grading.ai.GradingLlmProvider;
import com.swd392.aiviva.evaluation.grading.service.GradingWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grading")
@PreAuthorize("hasAnyRole('LE','AD')")
public class GradingController {
    private final GradingWorkflowService workflow;
    private final GradingLlmProvider provider;
    private final CurrentUserProvider currentUser;

    public GradingController(GradingWorkflowService workflow, GradingLlmProvider provider, CurrentUserProvider currentUser) {
        this.workflow = workflow;
        this.provider = provider;
        this.currentUser = currentUser;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<Object>> capabilities() {
        return ResponseEntity.ok(ApiResponse.success("Grading capabilities", workflow.capabilities(provider)));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<Object>> queue(@RequestParam(required = false) UUID examId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) OffsetDateTime finishedFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) OffsetDateTime finishedTo) {
        return ResponseEntity.ok(ApiResponse.success("Grading queue", workflow.queue(examId, status, page, size,
                finishedFrom, finishedTo, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @GetMapping("/queue/summary")
    public ResponseEntity<ApiResponse<Object>> summary(@RequestParam(required = false) UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Grading queue summary", workflow.summary(examId, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<Object>> reconcile() {
        return ResponseEntity.ok(ApiResponse.success("Completed attempts reconciled", workflow.reconcile(currentUser.currentUserId(), currentUser.isAdmin(), provider)));
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<ApiResponse<Object>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Grading submission", workflow.detail(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/claim")
    public ResponseEntity<ApiResponse<Object>> claim(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submission claimed", workflow.claim(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PutMapping("/submissions/{id}/lock")
    public ResponseEntity<ApiResponse<Object>> renewLock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submission claim renewed", workflow.renewLock(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/submissions/{id}/lock")
    public ResponseEntity<ApiResponse<Object>> releaseLock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submission claim released", workflow.releaseLock(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PutMapping("/submissions/{id}/scores")
    public ResponseEntity<ApiResponse<Object>> saveScores(@PathVariable UUID id, @Valid @RequestBody SaveScoresRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lecturer scores saved", workflow.saveScores(id, request, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PutMapping("/submissions/{id}/questions/{questionId}/notes")
    public ResponseEntity<ApiResponse<Object>> saveQuestionNote(@PathVariable UUID id, @PathVariable UUID questionId,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lecturer question note saved",
                workflow.saveQuestionNote(id, questionId, request, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/accept-ai")
    public ResponseEntity<ApiResponse<Object>> acceptAi(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI suggestions accepted by lecturer", workflow.acceptAi(id, request.version(), currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/ai-runs")
    public ResponseEntity<ApiResponse<Object>> rerunAi(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(ApiResponse.success("AI grading run queued", workflow.enqueueAiRun(id, currentUser.currentUserId(), currentUser.isAdmin(), provider)));
    }

    @GetMapping("/submissions/{id}/ai-runs")
    public ResponseEntity<ApiResponse<Object>> aiRuns(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("AI grading runs", workflow.aiRuns(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @GetMapping("/submissions/{id}/audit")
    public ResponseEntity<ApiResponse<Object>> audit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submission audit events", workflow.audit(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @GetMapping("/submissions/{id}/revisions")
    public ResponseEntity<ApiResponse<Object>> revisions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Grade revisions", workflow.revisions(id, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @GetMapping("/exams/{examId}/analytics/agreement")
    public ResponseEntity<ApiResponse<Object>> agreement(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("AI and lecturer agreement", workflow.agreement(examId, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @GetMapping("/exams/{examId}/settings")
    public ResponseEntity<ApiResponse<Object>> settings(@PathVariable UUID examId) {
        return ResponseEntity.ok(ApiResponse.success("Grading settings", workflow.settings(examId, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PutMapping("/exams/{examId}/settings")
    public ResponseEntity<ApiResponse<Object>> updateSettings(@PathVariable UUID examId,
            @Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Grading settings updated",
                workflow.updateSettings(examId, request, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/finalize")
    public ResponseEntity<ApiResponse<Object>> finalize(@PathVariable UUID id, @Valid @RequestBody FinalizeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Final grade finalized", workflow.finalizeSubmission(id, request, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/release")
    public ResponseEntity<ApiResponse<Object>> release(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Final grade released", workflow.release(id, request.version(), currentUser.currentUserId(), currentUser.isAdmin())));
    }

    @PostMapping("/submissions/{id}/reopen")
    public ResponseEntity<ApiResponse<Object>> reopen(@PathVariable UUID id, @Valid @RequestBody ReopenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Submission reopened", workflow.reopen(id, request, currentUser.currentUserId(), currentUser.isAdmin())));
    }

    public record SaveScoresRequest(@jakarta.validation.constraints.NotEmpty java.util.List<CriterionScoreInput> scores, long version) { }
    public record CriterionScoreInput(UUID questionId, UUID criterionId,
            @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal score,
            @jakarta.validation.constraints.DecimalMin(value = "0.001") java.math.BigDecimal maxScore,
            @jakarta.validation.constraints.Pattern(regexp = "ACCEPTED_AI|OVERRIDDEN|MANUAL") String source,
            String overrideReason, String comment) { }
    public record FinalizeRequest(boolean confirm, long version, String note) { }
    public record ReopenRequest(@NotBlank String reason, String note, long version) { }
    public record VersionRequest(long version) { }
    public record NoteRequest(String lecturerComment, String studentVisibleComment,
            @jakarta.validation.constraints.Pattern(regexp = "DRAFT|APPROVED") String commentStatus, long version) { }
    public record SettingsRequest(boolean autoAiScoring,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int aiKSamples,
            @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal deltaReasonThreshold,
            boolean includeSignalsInGrading, boolean allowBulkAccept,
            @jakarta.validation.constraints.Pattern(regexp = "MANUAL|AUTO_AFTER_FINALIZE") String releasePolicy,
            @jakarta.validation.constraints.DecimalMin(value = "0.001") java.math.BigDecimal roundingStep,
            @jakarta.validation.constraints.DecimalMin(value = "0.001") java.math.BigDecimal scaleMax,
            @jakarta.validation.constraints.Pattern(regexp = "ZERO_REQUIRES_REVIEW|MANUAL_ONLY") String unansweredPolicy) { }
}
