package com.swd392.aiviva.system.dto.response;

import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import java.util.List;
import java.util.UUID;

public record SubjectResponse(
        UUID subjectId,
        String subjectCode,
        String subjectName,
        String description,
        List<UUID> lecturerIds,
        List<LecturerSummaryResponse> lecturers,
        List<SubjectTopicSummary> topics,
        long topicCount) {
}
