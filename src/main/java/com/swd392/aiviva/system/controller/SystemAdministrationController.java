package com.swd392.aiviva.system.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.system.application.SubjectCatalogService;
import com.swd392.aiviva.system.application.SystemAdministrationService;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import com.swd392.aiviva.system.dto.request.AddSubjectTopicsRequest;
import com.swd392.aiviva.system.dto.request.AssignLecturersRequest;
import com.swd392.aiviva.system.dto.request.CreateSubjectRequest;
import com.swd392.aiviva.system.dto.request.SaveSubjectRequest;
import com.swd392.aiviva.system.dto.request.UpdateLanguageDefaultsRequest;
import com.swd392.aiviva.system.dto.response.SubjectAssignmentsResponse;
import com.swd392.aiviva.system.dto.response.SubjectResponse;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/api/system/admin")
@PreAuthorize("hasRole('AD')")
public class SystemAdministrationController {
    private final SystemAdministrationService service;
    private final SubjectCatalogService subjects;

    public SystemAdministrationController(SystemAdministrationService service, SubjectCatalogService subjects) {
        this.service = service;
        this.subjects = subjects;
    }

    @GetMapping("/lecturers")
    public ResponseEntity<ApiResponse<List<LecturerSummaryResponse>>> lecturers() {
        return ResponseEntity.ok(ApiResponse.success("Lecturers retrieved successfully", service.lecturers()));
    }

    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> subjects(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success("Subjects retrieved successfully", subjects.list(keyword)));
    }

    @GetMapping("/subjects/{subjectId}")
    public ResponseEntity<ApiResponse<SubjectResponse>> subject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success("Subject retrieved successfully", subjects.get(subjectId)));
    }

    @PostMapping("/subjects")
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(@Valid @RequestBody CreateSubjectRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subject created successfully",
                        subjects.create(request, principal.getName())));
    }

    @PutMapping("/subjects/{subjectId}")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateSubject(@PathVariable UUID subjectId,
            @Valid @RequestBody SaveSubjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Subject updated successfully", subjects.update(subjectId, request)));
    }

    @PostMapping("/subjects/{subjectId}/topics")
    public ResponseEntity<ApiResponse<SubjectResponse>> addSubjectTopics(@PathVariable UUID subjectId,
            @Valid @RequestBody AddSubjectTopicsRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Topics added successfully",
                        subjects.addTopics(subjectId, request, principal.getName())));
    }

    @GetMapping("/lecturers/{lecturerId}/subjects")
    public ResponseEntity<ApiResponse<List<SubjectAssignmentsResponse>>> lecturerSubjects(@PathVariable UUID lecturerId) {
        return ResponseEntity.ok(ApiResponse.success("Lecturer subjects retrieved successfully", service.subjectsForLecturer(lecturerId)));
    }

    @PostMapping("/subjects/{subjectId}/lecturers")
    public ResponseEntity<ApiResponse<SubjectAssignmentsResponse>> assignLecturers(
            @PathVariable UUID subjectId, @Valid @RequestBody AssignLecturersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lecturers assigned successfully",
                service.assignLecturers(subjectId, request.getLecturerIds())));
    }

    @DeleteMapping("/subjects/{subjectId}/lecturers/{lecturerId}")
    public ResponseEntity<ApiResponse<SubjectAssignmentsResponse>> unassignLecturer(
            @PathVariable UUID subjectId, @PathVariable UUID lecturerId) {
        return ResponseEntity.ok(ApiResponse.success("Lecturer unassigned successfully",
                service.unassignLecturer(subjectId, lecturerId)));
    }

    @GetMapping("/language-defaults")
    public ResponseEntity<ApiResponse<LanguageDefaults>> languageDefaults() {
        return ResponseEntity.ok(ApiResponse.success("Language defaults retrieved successfully", service.languageDefaults()));
    }

    @PutMapping("/language-defaults")
    public ResponseEntity<ApiResponse<LanguageDefaults>> updateLanguageDefaults(
            @Valid @RequestBody UpdateLanguageDefaultsRequest request) {
        LanguageDefaults updated = service.updateLanguageDefaults(
                new LanguageDefaults(request.sttLanguage(), request.ttsLanguage()));
        return ResponseEntity.ok(ApiResponse.success("Language defaults updated successfully", updated));
    }
}
