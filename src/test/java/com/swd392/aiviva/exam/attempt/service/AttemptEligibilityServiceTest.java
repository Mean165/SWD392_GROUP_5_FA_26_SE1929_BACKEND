package com.swd392.aiviva.exam.attempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.repository.AttemptAuditRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.StudentAttemptAllowanceRepository;
import com.swd392.aiviva.exam.attempt.web.AttemptPolicyException;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.user.entity.AppUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttemptEligibilityServiceTest {
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final ExamAttemptSettingsRepository settings = mock(ExamAttemptSettingsRepository.class);
    private final StudentAttemptAllowanceRepository allowances = mock(StudentAttemptAllowanceRepository.class);
    private final AttemptAuditRepository audit = mock(AttemptAuditRepository.class);
    private final InteractionTurnRepository turns = mock(InteractionTurnRepository.class);
    private final StudentSessionAssignmentRepository assignments = mock(StudentSessionAssignmentRepository.class);
    private final UUID examId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-05T10:00:00Z");
    private StudentSessionAssignment enrollment;
    private ExamAttemptSettings policy;
    private AttemptEligibilityService service;

    @BeforeEach
    void setUp() {
        ExamSession exam = ExamSession.builder().sessionId(examId).status("IN_PROGRESS").timeLimitMinutes(30).build();
        enrollment = StudentSessionAssignment.builder().assignmentId(enrollmentId).examSession(exam)
                .student(AppUser.builder().userId(studentId).build()).status("COMPLETED").build();
        policy = new ExamAttemptSettings(); policy.setExamId(examId); policy.setMaxAttempts(2);
        service = new AttemptEligibilityService(attempts, settings, allowances, audit, turns, assignments);
        when(settings.findById(examId)).thenReturn(Optional.of(policy));
        when(allowances.findByExamSession_SessionIdAndStudent_UserId(examId, studentId)).thenReturn(Optional.empty());
    }

    @Test
    void allowsNextScheduledAttemptAfterFirstCountedAttempt() {
        ExamAttempt first = attempt(1, "COMPLETED", true, now.minusHours(1));
        ExamAttempt next = attempt(2, "SCHEDULED", false, now.minusMinutes(5));
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(first, next));
        assertSame(next, service.requireEligible(enrollment, next.getAttemptId(), now));
    }

    @Test
    void blocksWhenCountedAttemptLimitIsExhausted() {
        policy.setMaxAttempts(1);
        ExamAttempt first = attempt(1, "COMPLETED", true, now.minusHours(1));
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(first));
        assertEquals("ATTEMPTS_EXHAUSTED", rejected(first.getAttemptId()).getCode());
    }

    @Test
    void voidedAttemptDoesNotConsumeAttemptLimit() {
        policy.setMaxAttempts(1);
        ExamAttempt voided = attempt(1, "VOIDED", false, now.minusHours(1));
        ExamAttempt replacement = attempt(2, "SCHEDULED", false, now.minusMinutes(5));
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(voided, replacement));
        assertSame(replacement, service.requireEligible(enrollment, replacement.getAttemptId(), now));
    }

    @Test
    void blocksCooldownAndReturnsRetryTime() {
        policy.setCooldownMinutes(30);
        ExamAttempt first = attempt(1, "COMPLETED", true, now.minusMinutes(10));
        ExamAttempt next = attempt(2, "SCHEDULED", false, now.minusMinutes(5));
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(first, next));
        AttemptPolicyException exception = rejected(next.getAttemptId());
        assertEquals("COOLDOWN_ACTIVE", exception.getCode());
        assertEquals(now.plusMinutes(20), exception.getRetryAt());
    }

    @Test
    void blocksAttemptWhenAnotherAttemptIsAlreadyInProgress() {
        ExamAttempt active = attempt(1, "IN_PROGRESS", false, now.minusMinutes(1));
        ExamAttempt next = attempt(2, "SCHEDULED", false, now);
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(active, next));
        assertEquals("ATTEMPT_IN_PROGRESS", rejected(next.getAttemptId()).getCode());
    }

    @Test
    void blocksAttemptOutsideScheduledWindow() {
        ExamAttempt future = attempt(1, "SCHEDULED", false, now);
        future.setScheduledTime(now.plusHours(1));
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of(future));
        assertEquals("OUTSIDE_WINDOW", rejected(future.getAttemptId()).getCode());
    }

    @Test
    void unknownAttemptIsNotAnEnrollmentAuthorization() {
        UUID unknown = UUID.randomUUID();
        when(attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollmentId)).thenReturn(List.of());
        assertEquals("NOT_ENROLLED", rejected(unknown).getCode());
    }

    @Test
    void firstAskedQuestionIsTheCountingEventAndIsAudited() {
        ExamAttempt scheduled = attempt(1, "SCHEDULED", false, now);
        when(attempts.save(scheduled)).thenReturn(scheduled);
        service.markFirstQuestionAsked(scheduled, UUID.randomUUID(), now);
        org.junit.jupiter.api.Assertions.assertTrue(scheduled.isCounted());
        assertEquals("IN_PROGRESS", scheduled.getStatus());
        verify(audit).append(org.mockito.ArgumentMatchers.eq(examId), org.mockito.ArgumentMatchers.eq(studentId),
                org.mockito.ArgumentMatchers.eq(studentId), org.mockito.ArgumentMatchers.eq("STUDENT"),
                org.mockito.ArgumentMatchers.eq("ATTEMPT_COUNTED"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    private AttemptPolicyException rejected(UUID attemptId) {
        try {
            service.requireEligible(enrollment, attemptId, now);
        } catch (AttemptPolicyException exception) {
            return exception;
        }
        throw new AssertionError("Expected an attempt eligibility rejection");
    }

    private ExamAttempt attempt(int number, String status, boolean counted, OffsetDateTime completedAt) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setExamSession(enrollment.getExamSession());
        attempt.setAssignment(enrollment);
        attempt.setStudent(enrollment.getStudent());
        attempt.setAttemptNo(number);
        attempt.setStatus(status);
        attempt.setCounted(counted);
        attempt.setScheduledTime(number == 1 ? now.minusHours(2) : now.minusMinutes(5));
        attempt.setCompletedAt("COMPLETED".equals(status) ? completedAt : null);
        return attempt;
    }
}
