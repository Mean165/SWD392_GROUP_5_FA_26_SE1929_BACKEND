package com.swd392.aiviva.system.application;

import com.swd392.aiviva.question.application.SubjectAssignmentService;
import com.swd392.aiviva.question.dto.response.SubjectAssignmentRecord;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import com.swd392.aiviva.system.dto.response.SubjectAssignmentsResponse;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import com.swd392.aiviva.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SystemAdministrationService {
    private final UserService users;
    private final SubjectAssignmentService assignments;
    private final SystemLanguageDefaultsService languages;

    public SystemAdministrationService(UserService users, SubjectAssignmentService assignments,
            SystemLanguageDefaultsService languages) {
        this.users = users;
        this.assignments = assignments;
        this.languages = languages;
    }

    public List<LecturerSummaryResponse> lecturers() {
        return users.listLecturers();
    }

    public List<SubjectAssignmentsResponse> subjects() {
        Map<UUID, LecturerSummaryResponse> lecturersById = lecturers().stream()
                .collect(Collectors.toMap(LecturerSummaryResponse::userId, Function.identity()));
        return assignments.findAll().stream().map(item -> response(item, lecturersById)).toList();
    }

    public List<SubjectAssignmentsResponse> subjectsForLecturer(UUID lecturerId) {
        Map<UUID, LecturerSummaryResponse> lecturersById = Map.of(lecturerId, users.getLecturer(lecturerId));
        return assignments.findForLecturer(lecturerId).stream().map(item -> response(item, lecturersById)).toList();
    }

    public SubjectAssignmentsResponse assignLecturers(UUID subjectId, List<UUID> lecturerIds) {
        SubjectAssignmentRecord result = assignments.addLecturers(subjectId, lecturerIds);
        Map<UUID, LecturerSummaryResponse> lecturersById = lecturers().stream()
                .collect(Collectors.toMap(LecturerSummaryResponse::userId, Function.identity()));
        return response(result, lecturersById);
    }

    public SubjectAssignmentsResponse unassignLecturer(UUID subjectId, UUID lecturerId) {
        SubjectAssignmentRecord result = assignments.removeLecturer(subjectId, lecturerId);
        Map<UUID, LecturerSummaryResponse> lecturersById = lecturers().stream()
                .collect(Collectors.toMap(LecturerSummaryResponse::userId, Function.identity()));
        return response(result, lecturersById);
    }

    public LanguageDefaults languageDefaults() {
        return languages.currentDefaults();
    }

    public LanguageDefaults updateLanguageDefaults(LanguageDefaults defaults) {
        return languages.update(defaults);
    }

    private SubjectAssignmentsResponse response(SubjectAssignmentRecord record,
            Map<UUID, LecturerSummaryResponse> lecturersById) {
        List<LecturerSummaryResponse> resolved = record.lecturerIds().stream()
                .map(lecturersById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new SubjectAssignmentsResponse(record.subjectId(), record.subjectCode(), record.subjectName(),
                record.lecturerIds(), resolved);
    }
}
