package com.swd392.aiviva.question.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricMatrixResponse;
import com.swd392.aiviva.question.dto.response.RubricResolutionResponse;
import com.swd392.aiviva.question.dto.response.RubricResponse;
import com.swd392.aiviva.question.service.RubricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Rubrics")
public class RubricController {
    private final RubricService rubricService;
    public RubricController(RubricService rubricService) { this.rubricService = rubricService; }

    @GetMapping("/api/subjects/{subjectId}/rubric")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Get the active subject rubric")
    public ResponseEntity<ApiResponse<RubricResponse>> getActive(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success("Subject rubric retrieved", rubricService.getActiveRubric(subjectId)));
    }

    @PostMapping("/api/subjects/{subjectId}/rubric")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Create the active rubric for a subject")
    public ResponseEntity<ApiResponse<RubricResponse>> createForSubject(@PathVariable UUID subjectId,
            @Valid @RequestBody RubricRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Subject rubric created", rubricService.createRubric(subjectId, request)));
    }

    @PutMapping("/api/subjects/{subjectId}/rubric")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Update the active subject rubric")
    public ResponseEntity<ApiResponse<RubricResponse>> updateForSubject(@PathVariable UUID subjectId,
            @Valid @RequestBody RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subject rubric updated", rubricService.updateActiveRubric(subjectId, request)));
    }

    @DeleteMapping("/api/subjects/{subjectId}/rubric")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Delete or archive the active subject rubric")
    public ResponseEntity<ApiResponse<Void>> deleteForSubject(@PathVariable UUID subjectId) {
        rubricService.deleteActiveRubric(subjectId);
        return ResponseEntity.ok(ApiResponse.success("Subject rubric deleted or archived", null));
    }

    @GetMapping("/api/subjects/{subjectId}/rubrics")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> listForSubject(@PathVariable UUID subjectId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Subject rubrics retrieved", rubricService.listRubrics(subjectId, status)));
    }

    @GetMapping("/api/subjects/{subjectId}/rubric/stats")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Get approved-question counts by Bloom level for a subject")
    public ResponseEntity<ApiResponse<RubricMatrixResponse>> questionStats(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success("Subject question statistics retrieved", rubricService.questionStats(subjectId)));
    }

    @GetMapping("/api/topics/{topicId}/rubrics")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "List rubrics for a topic")
    public ResponseEntity<ApiResponse<List<RubricResponse>>> list(@PathVariable UUID topicId,
            @RequestParam(required = false) String bloomLevel, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Rubrics retrieved", rubricService.listRubricsForTopic(topicId, status)));
    }

    @GetMapping("/api/topics/{topicId}/rubrics/matrix")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<RubricMatrixResponse>> matrix(@PathVariable UUID topicId) {
        return ResponseEntity.ok(ApiResponse.success("Rubric matrix retrieved", rubricService.questionStatsForTopic(topicId)));
    }

    @PostMapping("/api/topics/{topicId}/rubrics")
    @PreAuthorize("hasAnyRole('LE','AD')")
    @Operation(summary = "Create a subject rubric")
    public ResponseEntity<ApiResponse<RubricResponse>> create(@PathVariable UUID topicId, @Valid @RequestBody RubricRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rubric created", rubricService.createRubricForTopic(topicId, request)));
    }

    @GetMapping("/api/rubrics/{rubricId}")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<RubricResponse>> get(@PathVariable UUID rubricId) {
        return ResponseEntity.ok(ApiResponse.success("Rubric retrieved", rubricService.getRubric(rubricId)));
    }

    @PutMapping("/api/rubrics/{rubricId}")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<RubricResponse>> update(@PathVariable UUID rubricId, @Valid @RequestBody RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rubric updated", rubricService.updateRubric(rubricId, request)));
    }

    @DeleteMapping("/api/rubrics/{rubricId}")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID rubricId) {
        rubricService.deleteRubric(rubricId); return ResponseEntity.ok(ApiResponse.success("Rubric deleted or archived", null));
    }

    @GetMapping("/api/questions/{questionId}/rubric")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<RubricResolutionResponse>> effective(@PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.success("Effective rubric retrieved", rubricService.getEffectiveRubric(questionId)));
    }

    @PutMapping("/api/questions/{questionId}/rubric-override")
    @PreAuthorize("hasAnyRole('LE','AD')")
    public ResponseEntity<ApiResponse<RubricResolutionResponse>> override(@PathVariable UUID questionId, @RequestBody(required = false) RubricRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question rubric override updated", rubricService.setQuestionOverride(questionId, request)));
    }
}
