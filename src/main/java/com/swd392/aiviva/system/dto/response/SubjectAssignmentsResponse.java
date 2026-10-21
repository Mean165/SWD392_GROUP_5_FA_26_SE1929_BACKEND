package com.swd392.aiviva.system.dto.response;

import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import java.util.List;
import java.util.UUID;

public record SubjectAssignmentsResponse(
        UUID subjectId,
        String subjectCode,
        String subjectName,
        List<UUID> lecturerIds,
        List<LecturerSummaryResponse> lecturers) {
}
