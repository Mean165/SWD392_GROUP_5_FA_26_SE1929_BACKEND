package com.swd392.aiviva.question.application;

import com.swd392.aiviva.question.dto.response.SubjectAssignmentRecord;
import java.util.List;
import java.util.UUID;

public interface SubjectAssignmentService {
    List<SubjectAssignmentRecord> findAll();

    List<SubjectAssignmentRecord> findForLecturer(UUID lecturerId);

    SubjectAssignmentRecord addLecturers(UUID subjectId, List<UUID> lecturerIds);

    SubjectAssignmentRecord removeLecturer(UUID subjectId, UUID lecturerId);
}
