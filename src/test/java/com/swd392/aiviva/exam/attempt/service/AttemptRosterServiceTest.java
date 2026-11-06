package com.swd392.aiviva.exam.attempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamStudentResultRepository;
import com.swd392.aiviva.exam.attempt.repository.StudentAttemptAllowanceRepository;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.user.entity.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttemptRosterServiceTest {
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final ExamAttemptSettingsRepository settings = mock(ExamAttemptSettingsRepository.class);
    private final StudentAttemptAllowanceRepository allowances = mock(StudentAttemptAllowanceRepository.class);
    private final ExamStudentResultRepository results = mock(ExamStudentResultRepository.class);
    private final FinalGradeReportRepository grades = mock(FinalGradeReportRepository.class);

    @Test
    void rosterCanRenderScheduledAttemptWithoutInterviewOrGrade() {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        ExamSession exam = ExamSession.builder().sessionId(examId).sessionName("Exam").build();
        AppUser student = AppUser.builder().userId(studentId).build();
        StudentSessionAssignment enrollment = StudentSessionAssignment.builder().assignmentId(assignmentId)
                .examSession(exam).student(student).status("SCHEDULED").build();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setAttemptId(UUID.randomUUID()); attempt.setAttemptNo(1); attempt.setStatus("SCHEDULED");
        attempt.setExamSession(exam); attempt.setStudent(student); attempt.setAssignment(enrollment);
        when(attempts.findByExamSession_SessionIdOrderByAssignment_SlotOrderAscAttemptNoAsc(examId)).thenReturn(List.of(attempt));
        when(settings.findById(examId)).thenReturn(Optional.of(new ExamAttemptSettings()));
        when(allowances.findByExamSession_SessionId(examId)).thenReturn(List.of());
        when(results.findByExamSession_SessionId(examId)).thenReturn(List.of());

        var response = new AttemptRosterService(attempts, settings, allowances, results, grades).map(examId, List.of(enrollment)).getFirst();

        assertEquals(1, response.getAttempts().size());
        assertEquals("SCHEDULED", response.getCurrentAttempt().getStatus());
        assertNull(response.getCurrentAttempt().getInterviewSessionId());
        assertNull(response.getCurrentAttempt().getFinalTotal());
        assertEquals(1, response.getAttemptsRemaining());
    }
}
