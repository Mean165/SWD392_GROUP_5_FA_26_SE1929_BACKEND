package com.swd392.aiviva.exam.attempt.service;

import com.swd392.aiviva.exam.attempt.domain.AttemptEligibilityDecision;
import com.swd392.aiviva.exam.attempt.domain.AttemptStatus;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.entity.StudentAttemptAllowance;
import com.swd392.aiviva.exam.attempt.repository.AttemptAuditRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.StudentAttemptAllowanceRepository;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.exam.attempt.web.AttemptPolicyException;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptEligibilityService {
    private static final List<String> ACTIVE = List.of("SCHEDULED", "IN_PROGRESS");
    private final ExamAttemptRepository attempts;
    private final ExamAttemptSettingsRepository settings;
    private final StudentAttemptAllowanceRepository allowances;
    private final AttemptAuditRepository audit;
    private final InteractionTurnRepository turns;
    private final StudentSessionAssignmentRepository assignments;

    public AttemptEligibilityService(ExamAttemptRepository attempts, ExamAttemptSettingsRepository settings,
            StudentAttemptAllowanceRepository allowances, AttemptAuditRepository audit,
            InteractionTurnRepository turns, StudentSessionAssignmentRepository assignments) {
        this.attempts = attempts;
        this.settings = settings;
        this.allowances = allowances;
        this.audit = audit;
        this.turns = turns;
        this.assignments = assignments;
    }

    @Transactional
    public StudentSessionAssignment findEnrollmentForAttempt(UUID examAttemptId, UUID studentId) {
        ExamAttempt attempt = attempts.findById(examAttemptId).orElseThrow(() ->
                reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Student is not enrolled in this exam", null));
        if (!studentId.equals(attempt.getStudent().getUserId())) {
            throw reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Student is not enrolled in this exam", null);
        }
        return assignments.findOwnedAssignmentForUpdate(attempt.getAssignment().getAssignmentId(), studentId)
                .orElseThrow(() -> reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Student is not enrolled in this exam", null));
    }

    @Transactional
    public ExamAttempt requireEligible(StudentSessionAssignment enrollment, UUID requestedAttemptId, OffsetDateTime now) {
        if (enrollment == null || enrollment.getRemovedAt() != null
                || "CANCELLED".equalsIgnoreCase(enrollment.getStatus())) {
            throw reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Student is not enrolled in this exam", null);
        }
        String examStatus = enrollment.getExamSession().getStatus();
        if ("CANCELLED".equalsIgnoreCase(examStatus) || "COMPLETED".equalsIgnoreCase(examStatus)) {
            throw reject(HttpStatus.CONFLICT, "EXAM_CLOSED", "Exam is closed", null);
        }
        UUID examId = enrollment.getExamSession().getSessionId();
        UUID studentId = enrollment.getStudent().getUserId();
        ExamAttemptSettings config = settings.findById(examId).orElseGet(() -> defaults(examId));
        int extra = allowances.findByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
        List<ExamAttempt> rows = attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId());
        int used = (int) rows.stream().filter(ExamAttempt::isCounted)
                .filter(row -> !AttemptStatus.VOIDED.name().equals(row.getStatus())).count();
        int allowed = Math.max(used, config.getMaxAttempts() + extra);
        if (requestedAttemptId != null && rows.stream().noneMatch(row -> requestedAttemptId.equals(row.getAttemptId()))) {
            throw reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Requested attempt does not belong to this enrollment", null);
        }
        ExamAttempt selected = requestedAttemptId == null
                ? rows.stream().filter(row -> "IN_PROGRESS".equals(row.getStatus())).findFirst()
                    .orElseGet(() -> rows.stream().filter(row -> "SCHEDULED".equals(row.getStatus()))
                            .min(Comparator.comparing(ExamAttempt::getAttemptNo)).orElse(null))
                : rows.stream().filter(row -> requestedAttemptId.equals(row.getAttemptId())).findFirst().orElse(null);
        if (selected == null) {
            throw reject(HttpStatus.CONFLICT, used >= allowed ? "ATTEMPTS_EXHAUSTED" : "OUTSIDE_WINDOW",
                    used >= allowed ? "No exam attempts remain" : "No scheduled attempt is available", null);
        }
        if (!enrollment.getAssignmentId().equals(selected.getAssignment().getAssignmentId())
                || !examId.equals(selected.getExamSession().getSessionId())
                || !studentId.equals(selected.getStudent().getUserId())) {
            throw reject(HttpStatus.FORBIDDEN, "NOT_ENROLLED", "Attempt does not belong to this enrollment", null);
        }
        if ("IN_PROGRESS".equals(selected.getStatus())) {
            if (rows.stream().anyMatch(row -> !row.getAttemptId().equals(selected.getAttemptId()) && ACTIVE.contains(row.getStatus()))) {
                throw reject(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS", "Another attempt is scheduled or in progress", null);
            }
            return selected;
        }
        if (!"SCHEDULED".equals(selected.getStatus())) {
            throw reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "This attempt is no longer available", null);
        }
        boolean otherActive = rows.stream().anyMatch(row -> !row.getAttemptId().equals(selected.getAttemptId())
                && ACTIVE.contains(row.getStatus()));
        if (otherActive) throw reject(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS", "Another attempt is scheduled or in progress", null);
        if (used >= allowed) throw reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "No exam attempts remain", null);
        if (config.getAttemptWindowStart() != null && now.isBefore(config.getAttemptWindowStart())) {
            throw reject(HttpStatus.CONFLICT, "OUTSIDE_WINDOW", "Attempt window has not opened", config.getAttemptWindowStart());
        }
        if (config.getAttemptWindowEnd() != null && now.isAfter(config.getAttemptWindowEnd())) {
            throw reject(HttpStatus.CONFLICT, "OUTSIDE_WINDOW", "Attempt window has closed", null);
        }
        if (selected.getScheduledTime() == null) {
            throw reject(HttpStatus.CONFLICT, "OUTSIDE_WINDOW", "Attempt has not been scheduled", null);
        }
        OffsetDateTime opensAt = selected.getScheduledTime().minusMinutes(10);
        OffsetDateTime closesAt = selected.getScheduledTime().plusMinutes(enrollment.getExamSession().getTimeLimitMinutes());
        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw reject(HttpStatus.CONFLICT, "OUTSIDE_WINDOW", "Attempt is outside its scheduled window", opensAt);
        }
        if (config.getCooldownMinutes() > 0) {
            OffsetDateTime lastCountedEnd = rows.stream().filter(ExamAttempt::isCounted)
                    .filter(row -> !AttemptStatus.VOIDED.name().equals(row.getStatus()))
                    .map(ExamAttempt::getCompletedAt).filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            if (lastCountedEnd != null) {
                OffsetDateTime retryAt = lastCountedEnd.plusMinutes(config.getCooldownMinutes());
                if (now.isBefore(retryAt)) throw reject(HttpStatus.CONFLICT, "COOLDOWN_ACTIVE",
                        "Cooldown is active", retryAt);
            }
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public AttemptEligibilityDecision inspect(StudentSessionAssignment enrollment, UUID requestedAttemptId, OffsetDateTime now) {
        int used = enrollment == null ? 0 : attemptsUsed(enrollment.getAssignmentId());
        int max = enrollment == null ? 1 : settings.findById(enrollment.getExamSession().getSessionId())
                .map(ExamAttemptSettings::getMaxAttempts).orElse(1);
        int extra = enrollment == null ? 0 : allowances.findByExamSession_SessionIdAndStudent_UserId(
                enrollment.getExamSession().getSessionId(), enrollment.getStudent().getUserId())
                .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
        int allowed = Math.max(used, max + extra);
        try {
            ExamAttempt next = requireEligible(enrollment, requestedAttemptId, now);
            return AttemptEligibilityDecision.allowed(next.getAttemptId(), next.getAttemptNo(), used, allowed);
        } catch (AttemptPolicyException exception) {
            return AttemptEligibilityDecision.rejected(exception.getCode(), exception.getRetryAt(),
                    requestedAttemptId == null ? 0 : attempts.findById(requestedAttemptId).map(ExamAttempt::getAttemptNo).orElse(0),
                    used, allowed, Math.max(0, allowed - used));
        }
    }

    @Transactional
    public void markFirstQuestionAsked(ExamAttempt attempt, UUID interviewSessionId, OffsetDateTime at) {
        if (attempt.isCounted()) return;
        attempt.setCounted(true);
        attempt.setStatus(AttemptStatus.IN_PROGRESS.name());
        attempt.setStartedAt(at);
        attempt.setInterviewSessionId(interviewSessionId);
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(),
                attempt.getStudent().getUserId(), "STUDENT", "ATTEMPT_COUNTED", null,
                java.util.Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "reason", "FIRST_QUESTION_ASKED"));
    }

    @Transactional
    public void markInterviewStarted(ExamAttempt attempt, UUID interviewSessionId, OffsetDateTime at) {
        if ("IN_PROGRESS".equals(attempt.getStatus())) return;
        if (!"SCHEDULED".equals(attempt.getStatus())) {
            throw reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "Attempt is no longer schedulable", null);
        }
        attempt.setStatus(AttemptStatus.IN_PROGRESS.name());
        attempt.setStartedAt(at);
        attempt.setInterviewSessionId(interviewSessionId);
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(),
                attempt.getStudent().getUserId(), "STUDENT", "ATTEMPT_STARTED", null,
                java.util.Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "counted", false));
    }

    @Transactional
    public boolean markCompleted(ExamAttempt attempt, OffsetDateTime completedAt) {
        attempt.setStatus(AttemptStatus.COMPLETED.name());
        attempt.setCompletedAt(completedAt);
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(),
                null, "SYSTEM", "ATTEMPT_COMPLETED", null,
                java.util.Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo()));
        return hasRemaining(attempt.getAssignment());
    }

    @Transactional
    public boolean markExpired(ExamAttempt attempt, OffsetDateTime expiredAt) {
        ExamAttemptSettings config = settings.findById(attempt.getExamSession().getSessionId())
                .orElseGet(() -> defaults(attempt.getExamSession().getSessionId()));
        attempt.setStatus(AttemptStatus.EXPIRED.name());
        if (config.isCountExpired()) attempt.setCounted(true);
        attempt.setCompletedAt(expiredAt);
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(), null,
                "SYSTEM", "ATTEMPT_EXPIRED", null,
                java.util.Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "counted", attempt.isCounted()));
        return hasRemaining(attempt.getAssignment());
    }

    @Transactional
    public boolean markAbandoned(ExamAttempt attempt, OffsetDateTime abandonedAt) {
        ExamAttemptSettings config = settings.findById(attempt.getExamSession().getSessionId())
                .orElseGet(() -> defaults(attempt.getExamSession().getSessionId()));
        attempt.setStatus(AttemptStatus.ABANDONED.name());
        attempt.setCounted(config.isCountAbandoned() && attempt.isCounted());
        attempt.setCompletedAt(abandonedAt);
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(), null,
                "SYSTEM", "ATTEMPT_ABANDONED", null,
                java.util.Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "counted", attempt.isCounted()));
        return hasRemaining(attempt.getAssignment());
    }

    @Transactional
    public boolean hasRemaining(StudentSessionAssignment enrollment) {
        enrollment = assignments.findBySessionAndStudentForUpdate(enrollment.getExamSession().getSessionId(),
                enrollment.getStudent().getUserId()).orElse(enrollment);
        UUID examId = enrollment.getExamSession().getSessionId();
        UUID studentId = enrollment.getStudent().getUserId();
        int max = settings.findById(examId).map(ExamAttemptSettings::getMaxAttempts).orElse(1);
        int extra = allowances.findByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
        List<ExamAttempt> rows = attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId());
        long used = rows.stream().filter(ExamAttempt::isCounted)
                .filter(row -> !AttemptStatus.VOIDED.name().equals(row.getStatus())).count();
        if (used >= max + extra) return false;
        if (rows.stream().anyMatch(row -> ACTIVE.contains(row.getStatus()))) return true;
        int nextNo = rows.stream().map(ExamAttempt::getAttemptNo).max(Comparator.naturalOrder()).orElse(0) + 1;
        ExamAttempt next = new ExamAttempt();
        next.setExamSession(enrollment.getExamSession());
        next.setStudent(enrollment.getStudent());
        next.setAssignment(enrollment);
        next.setAttemptNo(nextNo);
        next.setStatus(AttemptStatus.SCHEDULED.name());
        next.setCounted(false);
        next.setCreatedByType("SYSTEM");
        next.setCreatedAt(OffsetDateTime.now());
        attempts.save(next);
        if (!"SCHEDULED".equals(enrollment.getStatus())) {
            enrollment.setStatus("SCHEDULED");
            assignments.save(enrollment);
        }
        audit.append(examId, studentId, null, "SYSTEM", "ATTEMPT_CREATED", null,
                java.util.Map.of("attemptId", next.getAttemptId(), "attemptNo", nextNo));
        return true;
    }

    public int attemptsUsed(UUID assignmentId) {
        return (int) attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(assignmentId).stream()
                .filter(ExamAttempt::isCounted).filter(row -> !AttemptStatus.VOIDED.name().equals(row.getStatus())).count();
    }

    public Integer attemptNo(UUID examAttemptId) {
        return attempts.findById(examAttemptId).map(ExamAttempt::getAttemptNo).orElse(null);
    }

    public ExamAttempt findAttempt(UUID examAttemptId) {
        return attempts.findById(examAttemptId).orElseThrow(() ->
                reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "Attempt is no longer available", null));
    }

    @Transactional
    public ExamAttempt findAttemptForUpdate(UUID examAttemptId) {
        ExamAttempt attempt = attempts.findById(examAttemptId).orElseThrow(() ->
                reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "Attempt is no longer available", null));
        return attempts.findForUpdate(attempt.getAssignment().getAssignmentId(), attempt.getAttemptNo()).orElseThrow(() ->
                reject(HttpStatus.CONFLICT, "ATTEMPTS_EXHAUSTED", "Attempt is no longer available", null));
    }

    public java.util.Set<UUID> previouslyAskedQuestions(ExamAttempt attempt) {
        if (!settings.findById(attempt.getExamSession().getSessionId())
                .map(ExamAttemptSettings::isAvoidRepeatQuestions).orElse(true)) return java.util.Set.of();
        return attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(attempt.getAssignment().getAssignmentId()).stream()
                .filter(ExamAttempt::isCounted)
                .filter(previous -> previous.getAttemptNo() < attempt.getAttemptNo())
                .filter(previous -> previous.getInterviewSessionId() != null)
                .filter(previous -> !AttemptStatus.VOIDED.name().equals(previous.getStatus()))
                .flatMap(previous -> turns.findByAttempt_AttemptIdOrderByTurnOrderAsc(previous.getInterviewSessionId()).stream())
                .map(com.swd392.aiviva.interview.entity.InteractionTurn::getMainQuestion)
                .filter(java.util.Objects::nonNull)
                .map(com.swd392.aiviva.question.entity.Question::getQuestionId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private ExamAttemptSettings defaults(UUID examId) {
        ExamAttemptSettings settings = new ExamAttemptSettings();
        settings.setExamId(examId);
        return settings;
    }

    private AttemptPolicyException reject(HttpStatus status, String code, String message, OffsetDateTime retryAt) {
        return new AttemptPolicyException(status, code, message, retryAt);
    }

}
