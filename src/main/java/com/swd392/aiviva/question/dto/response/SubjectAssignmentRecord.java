package com.swd392.aiviva.question.dto.response;

import java.util.List;
import java.util.UUID;

public record SubjectAssignmentRecord(
        UUID subjectId,
        String subjectCode,
        String subjectName,
        List<UUID> lecturerIds) {
}
