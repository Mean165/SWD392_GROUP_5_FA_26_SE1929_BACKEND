package com.swd392.aiviva.exam.attempt.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.auth.service.CurrentUserProvider;
import com.swd392.aiviva.exam.attempt.domain.AttemptStatus;
import com.swd392.aiviva.exam.attempt.domain.ScorePolicy;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.entity.ExamStudentResult;
import com.swd392.aiviva.exam.attempt.entity.StudentAttemptAllowance;
import com.swd392.aiviva.exam.attempt.repository.AttemptAuditRepository;
import com.swd392.aiviva.exam.attempt.web.AttemptPolicyException;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamStudentResultRepository;
import com.swd392.aiviva.exam.attempt.repository.StudentAttemptAllowanceRepository;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.dto.request.ScheduleExamRequest;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.repository.SubjectRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAttemptService {
    private final ExamAttemptRepository attempts;
    private final ExamAttemptSettingsRepository settings;
    private final StudentAttemptAllowanceRepository allowances;
    private final ExamStudentResultRepository results;
    private final StudentSessionAssignmentRepository assignments;
    private final ExamSessionRepository exams;
    private final UserRepository users;
    private final SubjectRepository subjects;
    private final CurrentUserProvider currentUser;
    private final AttemptEligibilityService eligibility;
    private final OfficialExamResultService officialResults;
    private final AttemptAuditRepository audit;
    private final VivaAttemptRepository interviews;
    private final JdbcTemplate jdbc;
    private final AttemptRosterService roster;

    public ExamAttemptService(ExamAttemptRepository attempts, ExamAttemptSettingsRepository settings,
            StudentAttemptAllowanceRepository allowances, ExamStudentResultRepository results,
            StudentSessionAssignmentRepository assignments, ExamSessionRepository exams, UserRepository users,
            SubjectRepository subjects, CurrentUserProvider currentUser, AttemptEligibilityService eligibility,
            OfficialExamResultService officialResults, AttemptAuditRepository audit, VivaAttemptRepository interviews,
            JdbcTemplate jdbc, AttemptRosterService roster) {
        this.attempts = attempts;
        this.settings = settings;
        this.allowances = allowances;
        this.results = results;
        this.assignments = assignments;
        this.exams = exams;
        this.users = users;
        this.subjects = subjects;
        this.currentUser = currentUser;
        this.eligibility = eligibility;
        this.officialResults = officialResults;
        this.audit = audit;
        this.interviews = interviews;
        this.jdbc = jdbc;
        this.roster = roster;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings(UUID examId) {
        authorize(examId);
        ExamAttemptSettings config = settings.findById(examId).orElseGet(() -> defaultSettings(examId));
        return settingsResponse(config, List.of());
    }

    @Transactional
    public Map<String, Object> updateSettings(UUID examId, SettingsRequest request) {
        authorize(examId);
        exams.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        if (request.scorePolicy() == null || request.scorePolicy().isBlank()) throw new BusinessException("scorePolicy is required");
        if (request.maxAttempts() < 1 || request.cooldownMinutes() < 0) throw new BusinessException("maxAttempts must be >= 1 and cooldownMinutes must be >= 0");
        if (request.attemptWindowStart() != null && request.attemptWindowEnd() != null
                && !request.attemptWindowEnd().isAfter(request.attemptWindowStart())) {
            throw new BusinessException("attemptWindowEnd must be after attemptWindowStart");
        }
        ScorePolicy policy;
        try { policy = ScorePolicy.valueOf(request.scorePolicy()); }
        catch (IllegalArgumentException exception) { throw new BusinessException("Unsupported scorePolicy"); }
        ExamAttemptSettings config = settings.findById(examId).orElseGet(() -> defaultSettings(examId));
        if (config.getVersion() != request.version()) throw new ConflictException("Attempt settings changed; reload before saving", "STALE_VERSION");
        assignments.findByExamSessionForUpdate(examId);
        int priorMax = config.getMaxAttempts();
        int priorCooldown = config.getCooldownMinutes();
        String priorPolicy = config.getScorePolicy();
        boolean priorAvoidRepeat = config.isAvoidRepeatQuestions();
        OffsetDateTime priorWindowStart = config.getAttemptWindowStart();
        OffsetDateTime priorWindowEnd = config.getAttemptWindowEnd();
        boolean priorExtraAllowed = config.isAllowLecturerExtraAttempts();
        boolean priorCountAbandoned = config.isCountAbandoned();
        boolean priorCountExpired = config.isCountExpired();
        config.setMaxAttempts(request.maxAttempts());
        config.setScorePolicy(policy.name());
        config.setAvoidRepeatQuestions(request.avoidRepeatQuestions());
        config.setCooldownMinutes(request.cooldownMinutes());
        config.setAttemptWindowStart(request.attemptWindowStart());
        config.setAttemptWindowEnd(request.attemptWindowEnd());
        config.setCountOnStartEvent("FIRST_QUESTION_ASKED");
        config.setAllowLecturerExtraAttempts(request.allowLecturerExtraAttempts());
        config.setCountAbandoned(request.countAbandoned());
        config.setCountExpired(request.countExpired());
        config.setUpdatedBy(users.findById(currentUser.currentUserId()).orElseThrow());
        config.setUpdatedAt(OffsetDateTime.now());
        settings.save(config);
        List<Map<String, Object>> affected = loweredLimitWarnings(examId, request.maxAttempts());
        audit.appendExam(examId, currentUser.currentUserId(), actorType(), "ATTEMPT_SETTINGS_UPDATED", "Lecturer changed attempt settings",
                Map.of("before", Map.of("maxAttempts", priorMax, "scorePolicy", priorPolicy, "cooldownMinutes", priorCooldown,
                                "avoidRepeatQuestions", priorAvoidRepeat, "attemptWindowStart", priorWindowStart == null ? "" : priorWindowStart,
                                "attemptWindowEnd", priorWindowEnd == null ? "" : priorWindowEnd, "allowLecturerExtraAttempts", priorExtraAllowed,
                                "countAbandoned", priorCountAbandoned, "countExpired", priorCountExpired),
                        "after", Map.of("maxAttempts", request.maxAttempts(), "scorePolicy", policy.name(),
                                "cooldownMinutes", request.cooldownMinutes(), "avoidRepeatQuestions", request.avoidRepeatQuestions(),
                                "attemptWindowStart", request.attemptWindowStart() == null ? "" : request.attemptWindowStart(),
                                "attemptWindowEnd", request.attemptWindowEnd() == null ? "" : request.attemptWindowEnd(),
                                "allowLecturerExtraAttempts", request.allowLecturerExtraAttempts(),
                                "countAbandoned", request.countAbandoned(), "countExpired", request.countExpired()),
                        "studentsAboveLimit", affected.size()));
        for (StudentSessionAssignment assignment : assignments.findByExamSessionSessionId(examId)) {
            int extra = allowances.findByExamSession_SessionIdAndStudent_UserId(examId, assignment.getStudent().getUserId())
                    .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
            int limit = request.maxAttempts() + extra;
            for (ExamAttempt attempt : attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(assignment.getAssignmentId())) {
                if (attempt.getAttemptNo() > limit && "SCHEDULED".equals(attempt.getStatus()) && !attempt.isCounted()) {
                    attempt.setStatus("CANCELLED");
                    attempts.save(attempt);
                    audit.append(examId, assignment.getStudent().getUserId(), currentUser.currentUserId(), actorType(),
                            "UNUSED_ATTEMPT_CANCELLED_AFTER_LIMIT_REDUCTION", null,
                            Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "newLimit", limit));
                }
            }
            eligibility.hasRemaining(assignment);
            officialResults.recompute(examId, assignment.getStudent().getUserId(), currentUser.currentUserId(), actorType(), "ATTEMPT_POLICY_CHANGED");
        }
        return settingsResponse(config, affected);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentAttempts(UUID examId, UUID studentId) {
        authorize(examId);
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        List<ExamAttempt> rows = attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId());
        int used = eligibility.attemptsUsed(enrollment.getAssignmentId());
        int allowed = effectiveLimit(examId, studentId, used);
        return rows.stream().map(this::attemptResponse).peek(item -> {
            item.put("attemptsUsed", used);
            item.put("attemptsAllowed", allowed);
            item.put("attemptsRemaining", Math.max(0, allowed - used));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> rowsForEnrollment(UUID examId, UUID studentId) {
        authorize(examId);
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        return attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId());
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> scheduledAttempts(UUID examId) {
        authorize(examId);
        return attempts.findByExamSession_SessionIdAndAttemptNoOrderByScheduledTimeAsc(examId, 1).stream()
                .filter(row -> row.getAssignment().getRemovedAt() == null && !"CANCELLED".equals(row.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public ExamAttempt findAttemptForEnrollment(UUID examId, UUID studentId, UUID attemptId) {
        return rowsForEnrollment(examId, studentId).stream().filter(row -> row.getAttemptId().equals(attemptId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
    }

    @Transactional
    public Map<String, Object> createScheduledAttempt(UUID examId, UUID studentId, OffsetDateTime scheduledTime) {
        authorize(examId);
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        if (scheduledTime == null) throw new BusinessException("scheduledTime is required");
        eligibility.hasRemaining(enrollment);
        ExamAttempt next = attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId()).stream()
                .filter(row -> "SCHEDULED".equals(row.getStatus()) && row.getScheduledTime() == null)
                .min(Comparator.comparing(ExamAttempt::getAttemptNo))
                .orElseThrow(() -> new ConflictException("No additional attempt is currently available", "ATTEMPTS_EXHAUSTED"));
        schedule(next, scheduledTime, null);
        return attemptResponse(next);
    }

    @Transactional
    public Map<String, Object> autoSchedule(UUID examId, ScheduleExamRequest request) {
        authorize(examId);
        ExamSession exam = exams.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        int attemptNo = request.getAttemptNo() == null ? 1 : request.getAttemptNo();
        if (attemptNo < 1) throw new BusinessException("attemptNo must be >= 1");
        OffsetDateTime start = request.getStartTime() == null ? exam.getStartTime() : request.getStartTime();
        if (start == null) start = OffsetDateTime.now();
        int slotMinutes = request.getSlotMinutes() == null
                ? exam.getTimeLimitMinutes() + exam.getBufferMinutes() : request.getSlotMinutes();
        if (slotMinutes < 1) throw new BusinessException("slotMinutes must be >= 1");
        List<ExamAttempt> targets = new ArrayList<>(attempts.findByExamSession_SessionIdAndAttemptNoOrderByScheduledTimeAsc(examId, attemptNo)
                .stream().filter(row -> "SCHEDULED".equals(row.getStatus()) && !row.isCounted()).toList());
        if (Boolean.TRUE.equals(request.getShuffle())) {
            long seed = request.getSeed() == null ? examId.getMostSignificantBits() ^ attemptNo : request.getSeed();
            java.util.Collections.shuffle(targets, new java.util.Random(seed));
        }
        java.util.Set<UUID> targetIds = targets.stream().map(ExamAttempt::getAttemptId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> rows = new ArrayList<>();
        OffsetDateTime cursor = start;
        boolean anyConflict = false;
        for (int i = 0; i < targets.size(); i++) {
            ExamAttempt target = targets.get(i);
            String conflict = conflict(target, cursor, targetIds, rows, exam.getTimeLimitMinutes());
            anyConflict |= conflict != null;
            Map<String, Object> row = new HashMap<>();
            row.put("examAttemptId", target.getAttemptId()); row.put("assignmentId", target.getAssignment().getAssignmentId());
            row.put("studentId", target.getStudent().getUserId()); row.put("studentName", target.getStudent().getFullName());
            row.put("attemptNo", attemptNo); row.put("slotOrder", i + 1); row.put("scheduledTime", cursor); row.put("conflict", conflict);
            rows.add(row);
            cursor = cursor.plusMinutes(slotMinutes);
        }
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun && anyConflict) throw new ConflictException("Auto schedule contains overlapping attempt slots", "SCHEDULE_CONFLICT");
        if (!dryRun) {
            for (int i = 0; i < targets.size(); i++) schedule(targets.get(i), (OffsetDateTime) rows.get(i).get("scheduledTime"), i + 1);
        }
        return Map.of("dryRun", dryRun, "attemptNo", attemptNo, "items", rows);
    }

    private String conflict(ExamAttempt target, OffsetDateTime proposed, java.util.Set<UUID> excluded,
            List<Map<String, Object>> proposedRows, int duration) {
        for (ExamAttempt other : attempts.findByStudent_UserId(target.getStudent().getUserId())) {
            if (excluded.contains(other.getAttemptId()) || other.getScheduledTime() == null
                    || List.of("CANCELLED","VOIDED","EXPIRED").contains(other.getStatus())) continue;
            int otherDuration = other.getExamSession().getTimeLimitMinutes();
            if (proposed.isBefore(other.getScheduledTime().plusMinutes(otherDuration))
                    && other.getScheduledTime().isBefore(proposed.plusMinutes(duration))) return "Student has another overlapping attempt";
        }
        for (ExamAttempt other : attempts.findByExamSession_SessionIdOrderByAssignment_SlotOrderAscAttemptNoAsc(target.getExamSession().getSessionId())) {
            if (excluded.contains(other.getAttemptId()) || other.getScheduledTime() == null
                    || List.of("CANCELLED","VOIDED","EXPIRED").contains(other.getStatus())) continue;
            if (proposed.isBefore(other.getScheduledTime().plusMinutes(duration))
                    && other.getScheduledTime().isBefore(proposed.plusMinutes(duration))) return "Attempt slot overlaps another student";
        }
        for (Map<String, Object> other : proposedRows) {
            OffsetDateTime otherTime = (OffsetDateTime) other.get("scheduledTime");
            if (proposed.isBefore(otherTime.plusMinutes(duration)) && otherTime.isBefore(proposed.plusMinutes(duration))) {
                return "Attempt slot overlaps another scheduled slot";
            }
        }
        return null;
    }

    @Transactional
    public Map<String, Object> schedule(UUID attemptId, OffsetDateTime scheduledTime, Integer slotOrder) {
        ExamAttempt attempt = attempts.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        authorize(attempt.getExamSession().getSessionId());
        requireEnrollment(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId());
        attempt = attempts.findForUpdate(attempt.getAssignment().getAssignmentId(), attempt.getAttemptNo())
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        if (!"SCHEDULED".equals(attempt.getStatus()) || attempt.isCounted()) {
            throw new ConflictException("Only an unused scheduled attempt can be scheduled", "ATTEMPT_NOT_SCHEDULABLE");
        }
        schedule(attempt, scheduledTime, slotOrder);
        return attemptResponse(attempts.findById(attemptId).orElseThrow());
    }

    @Transactional
    public Map<String, Object> scheduleEnrollmentAttempt(UUID examId, UUID studentId, int attemptNo,
            OffsetDateTime scheduledTime) {
        authorize(examId);
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        ExamAttempt attempt = attempts.findByAssignment_AssignmentIdAndAttemptNo(enrollment.getAssignmentId(), attemptNo)
                .orElseThrow(() -> new ConflictException("Attempt number is not currently allocated", "ATTEMPT_NOT_FOUND"));
        return schedule(attempt.getAttemptId(), scheduledTime, attempt.getSlotOrder());
    }

    private void schedule(ExamAttempt attempt, OffsetDateTime scheduledTime, Integer slotOrder) {
        if (scheduledTime == null) throw new BusinessException("scheduledTime is required");
        ensureNoTimeConflict(attempt, scheduledTime);
        attempt.setScheduledTime(scheduledTime);
        if (slotOrder != null) attempt.setSlotOrder(slotOrder);
        attempt.setCreatedByType("LECTURER");
        attempts.save(attempt);
        if (attempt.getAttemptNo() == 1) {
            StudentSessionAssignment enrollment = attempt.getAssignment();
            enrollment.setScheduledTime(scheduledTime);
            enrollment.setSlotOrder(attempt.getSlotOrder());
            enrollment.setStatus("SCHEDULED");
            assignments.save(enrollment);
        } else if (!"SCHEDULED".equals(attempt.getAssignment().getStatus())) {
            attempt.getAssignment().setStatus("SCHEDULED");
            assignments.save(attempt.getAssignment());
        }
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(), currentUser.currentUserId(), actorType(),
                "ATTEMPT_SCHEDULED", null, Map.of("attemptId", attempt.getAttemptId(), "attemptNo", attempt.getAttemptNo(), "scheduledTime", scheduledTime));
    }

    @Transactional
    public Map<String, Object> cancel(UUID attemptId, String reason) {
        if (reason == null || reason.isBlank()) throw new AttemptPolicyException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "REASON_REQUIRED", "reason is required", null);
        ExamAttempt initial = attempts.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        requireEnrollment(initial.getExamSession().getSessionId(), initial.getStudent().getUserId());
        ExamAttempt attempt = attempts.findForUpdate(initial.getAssignment().getAssignmentId(), initial.getAttemptNo())
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        authorize(attempt.getExamSession().getSessionId());
        if (!"SCHEDULED".equals(attempt.getStatus()) || attempt.isCounted() || attempt.getInterviewSessionId() != null) {
            throw new ConflictException("Only an unused scheduled attempt can be cancelled", "ATTEMPT_ALREADY_STARTED");
        }
        attempt.setStatus("CANCELLED");
        attempts.save(attempt);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(), currentUser.currentUserId(), actorType(),
                "ATTEMPT_CANCELLED", reason, Map.of("attemptId", attemptId, "attemptNo", attempt.getAttemptNo()));
        return attemptResponse(attempt);
    }

    @Transactional
    public Map<String, Object> voidAttempt(UUID attemptId, String reason) {
        if (reason == null || reason.isBlank()) throw new AttemptPolicyException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "REASON_REQUIRED", "reason is required", null);
        ExamAttempt initial = attempts.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        requireEnrollment(initial.getExamSession().getSessionId(), initial.getStudent().getUserId());
        ExamAttempt attempt = attempts.findForUpdate(initial.getAssignment().getAssignmentId(), initial.getAttemptNo())
                .orElseThrow(() -> new ResourceNotFoundException("Exam attempt not found"));
        authorize(attempt.getExamSession().getSessionId());
        String submissionStatus = jdbc.query("select status from grading_submissions where exam_attempt_id=?",
                rs -> rs.next() ? rs.getString(1) : null, attemptId);
        Boolean releasedOfficial = jdbc.queryForObject("""
                select exists(select 1 from exam_student_results r
                  left join viva_attempt va on va.exam_attempt_id=?
                  where r.exam_id=? and r.student_id=? and r.released=true
                    and (r.official_attempt_id=? or r.policy_applied='AVERAGE'))
                """, Boolean.class, attemptId, attempt.getExamSession().getSessionId(),
                attempt.getStudent().getUserId(), attemptId);
        if ("RELEASED".equals(submissionStatus) || Boolean.TRUE.equals(releasedOfficial)) {
            throw new ConflictException("A released attempt cannot be voided; reopen through the appeal workflow", "CANNOT_VOID_RELEASED");
        }
        if ("VOIDED".equals(attempt.getStatus())) return attemptResponse(attempt);
        attempt.setStatus("VOIDED");
        attempt.setCounted(false);
        attempt.setVoidReason(reason.trim());
        attempt.setVoidedBy(users.findById(currentUser.currentUserId()).orElseThrow());
        attempt.setVoidedAt(OffsetDateTime.now());
        attempts.save(attempt);
        jdbc.update("update grading_submissions set status='VOIDED',ai_status=case when ai_status='RUNNING' then 'FAILED' else ai_status end,version=version+1,updated_at=current_timestamp where exam_attempt_id=? and status <> 'RELEASED'", attemptId);
        audit.append(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(), currentUser.currentUserId(), actorType(),
                "ATTEMPT_VOIDED", reason.trim(), Map.of("attemptId", attemptId, "attemptNo", attempt.getAttemptNo()));
        eligibility.hasRemaining(attempt.getAssignment());
        officialResults.recompute(attempt.getExamSession().getSessionId(), attempt.getStudent().getUserId(),
                currentUser.currentUserId(), actorType(), "ATTEMPT_VOIDED");
        return attemptResponse(attempt);
    }

    @Transactional
    public Map<String, Object> grant(UUID examId, UUID studentId, int additional, String reason) {
        authorize(examId);
        if (additional < 1) throw new BusinessException("additional must be >= 1");
        if (reason == null || reason.isBlank()) throw new AttemptPolicyException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "REASON_REQUIRED", "reason is required", null);
        ExamAttemptSettings config = requireSettings(examId);
        if (!config.isAllowLecturerExtraAttempts()) throw new ConflictException("Extra attempts are disabled for this exam", "EXTRA_ATTEMPTS_DISABLED");
        ExamSession exam = exams.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        if ("CANCELLED".equalsIgnoreCase(exam.getStatus())) throw new ConflictException("Cancelled exam cannot accept extra attempts", "EXAM_CLOSED");
        if ("COMPLETED".equalsIgnoreCase(exam.getStatus())) throw new ConflictException("Completed exam must be reopened before granting extra attempts", "EXAM_CLOSED");
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        StudentAttemptAllowance allowance = allowances.findForUpdateByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .orElseGet(() -> newAllowance(examId, studentId));
        int before = allowance.getExtraAttempts();
        allowance.setExtraAttempts(before + additional);
        allowance.setReason(reason.trim());
        allowance.setGrantedBy(users.findById(currentUser.currentUserId()).orElseThrow());
        allowance.setGrantedAt(OffsetDateTime.now());
        allowance = allowances.save(allowance);
        audit.append(examId, studentId, currentUser.currentUserId(), actorType(), "ATTEMPTS_GRANTED", reason.trim(),
                Map.of("before", before, "additional", additional, "after", allowance.getExtraAttempts()));
        eligibility.hasRemaining(enrollment);
        return Map.of("extraAttempts", allowance.getExtraAttempts(), "attemptsUsed", eligibility.attemptsUsed(enrollment.getAssignmentId()),
                "attemptsAllowed", config.getMaxAttempts() + allowance.getExtraAttempts());
    }

    @Transactional
    public Map<String, Object> revoke(UUID examId, UUID studentId, int additional, String reason) {
        authorize(examId);
        if (additional < 1) throw new BusinessException("additional must be >= 1");
        if (reason == null || reason.isBlank()) throw new AttemptPolicyException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "REASON_REQUIRED", "reason is required", null);
        StudentSessionAssignment enrollment = requireEnrollment(examId, studentId);
        ExamAttemptSettings config = requireSettings(examId);
        StudentAttemptAllowance allowance = allowances.findForUpdateByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .orElseThrow(() -> new ConflictException("Student has no extra-attempt allowance", "ALLOWANCE_NOT_FOUND"));
        int used = eligibility.attemptsUsed(enrollment.getAssignmentId());
        int extraUsed = Math.max(0, used - config.getMaxAttempts());
        int next = allowance.getExtraAttempts() - additional;
        if (next < extraUsed || next < 0) throw new ConflictException("Used extra attempts cannot be revoked", "ALLOWANCE_ALREADY_USED");
        int before = allowance.getExtraAttempts();
        allowance.setExtraAttempts(next);
        allowance.setReason(reason.trim());
        allowance.setGrantedBy(users.findById(currentUser.currentUserId()).orElseThrow());
        allowance.setGrantedAt(OffsetDateTime.now());
        allowances.save(allowance);
        int newLimit = config.getMaxAttempts() + next;
        attempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(enrollment.getAssignmentId()).stream()
                .filter(row -> row.getAttemptNo() > newLimit && "SCHEDULED".equals(row.getStatus()) && !row.isCounted())
                .forEach(row -> { row.setStatus("CANCELLED"); attempts.save(row); });
        audit.append(examId, studentId, currentUser.currentUserId(), actorType(), "ATTEMPTS_REVOKED", reason.trim(),
                Map.of("before", before, "revoked", additional, "after", next));
        return Map.of("extraAttempts", next, "attemptsUsed", used, "attemptsAllowed", config.getMaxAttempts() + next);
    }

    @Transactional
    public Map<String, Object> chooseOfficialAttempt(UUID examId, UUID studentId, UUID attemptId, boolean confirm) {
        authorize(examId);
        AppUser user = users.findById(currentUser.currentUserId()).orElseThrow();
        ExamStudentResult result = officialResults.choose(examId, studentId, attemptId, confirm, user);
        return Map.of("policy", result.getPolicyApplied(), "officialAttemptId", result.getOfficialAttemptId(), "computedTotal", result.getComputedTotal() == null ? "" : result.getComputedTotal());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(UUID examId) {
        authorize(examId);
        return audit.findByExam(examId);
    }

    @Transactional(readOnly = true)
    public boolean extraAttemptsAllowed(UUID examId) {
        return requireSettings(examId).isAllowLecturerExtraAttempts();
    }

    @Transactional
    public void auditEnrollmentRemoved(UUID examId, UUID studentId, String reason) {
        authorize(examId);
        audit.append(examId, studentId, currentUser.currentUserId(), actorType(), "STUDENT_REMOVED_FROM_EXAM",
                reason, Map.of("studentId", studentId));
    }

    @Transactional(readOnly = true)
    public List<com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse> officialResults(UUID examId) {
        authorize(examId);
        return roster.map(examId, assignments.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(examId));
    }

    private List<Map<String, Object>> loweredLimitWarnings(UUID examId, int newMax) {
        List<Map<String, Object>> affected = new ArrayList<>();
        for (StudentSessionAssignment enrollment : assignments.findByExamSessionSessionId(examId)) {
            int used = eligibility.attemptsUsed(enrollment.getAssignmentId());
            int extra = allowances.findByExamSession_SessionIdAndStudent_UserId(examId, enrollment.getStudent().getUserId())
                    .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
            if (used > newMax + extra) affected.add(Map.of("studentId", enrollment.getStudent().getUserId(), "attemptsUsed", used, "effectiveLimit", used));
        }
        return affected;
    }

    private Map<String, Object> settingsResponse(ExamAttemptSettings config, List<Map<String, Object>> affected) {
        Map<String, Object> response = new HashMap<>();
        response.put("maxAttempts", config.getMaxAttempts()); response.put("scorePolicy", config.getScorePolicy());
        response.put("avoidRepeatQuestions", config.isAvoidRepeatQuestions()); response.put("cooldownMinutes", config.getCooldownMinutes());
        response.put("attemptWindowStart", config.getAttemptWindowStart()); response.put("attemptWindowEnd", config.getAttemptWindowEnd());
        response.put("countOnStartEvent", config.getCountOnStartEvent()); response.put("allowLecturerExtraAttempts", config.isAllowLecturerExtraAttempts());
        response.put("countAbandoned", config.isCountAbandoned()); response.put("countExpired", config.isCountExpired());
        response.put("affectedStudents", affected); response.put("version", config.getVersion());
        return response;
    }

    private ExamAttemptSettings requireSettings(UUID examId) {
        return settings.findById(examId).orElseGet(() -> defaultSettings(examId));
    }

    private int effectiveLimit(UUID examId, UUID studentId, int used) {
        int maximum = requireSettings(examId).getMaxAttempts();
        int extra = allowances.findByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .map(StudentAttemptAllowance::getExtraAttempts).orElse(0);
        return Math.max(used, maximum + extra);
    }

    private ExamAttemptSettings defaultSettings(UUID examId) {
        ExamAttemptSettings config = new ExamAttemptSettings(); config.setExamId(examId); return config;
    }

    private StudentSessionAssignment requireEnrollment(UUID examId, UUID studentId) {
        return assignments.findBySessionAndStudentForUpdate(examId, studentId)
                .filter(row -> row.getRemovedAt() == null)
                .orElseThrow(() -> new AttemptPolicyException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "NOT_ENROLLED", "Student is not enrolled in this exam", null));
    }

    private StudentAttemptAllowance newAllowance(UUID examId, UUID studentId) {
        StudentAttemptAllowance allowance = new StudentAttemptAllowance();
        allowance.setExamSession(exams.findById(examId).orElseThrow());
        allowance.setStudent(users.findById(studentId).orElseThrow());
        allowance.setExtraAttempts(0);
        return allowance;
    }

    private Map<String, Object> attemptResponse(ExamAttempt attempt) {
        Map<String, Object> response = new HashMap<>();
        response.put("examAttemptId", attempt.getAttemptId()); response.put("attemptNo", attempt.getAttemptNo());
        response.put("examId", attempt.getExamSession().getSessionId()); response.put("studentId", attempt.getStudent().getUserId());
        response.put("status", attempt.getStatus()); response.put("scheduledTime", attempt.getScheduledTime());
        response.put("slotOrder", attempt.getSlotOrder()); response.put("interviewSessionId", attempt.getInterviewSessionId());
        response.put("counted", attempt.isCounted()); response.put("voided", "VOIDED".equals(attempt.getStatus()));
        response.put("version", attempt.getVersion());
        return response;
    }

    private void ensureNoTimeConflict(ExamAttempt target, OffsetDateTime time) {
        int duration = target.getExamSession().getTimeLimitMinutes();
        for (ExamAttempt other : attempts.findByStudent_UserId(target.getStudent().getUserId())) {
            if (other.getAttemptId().equals(target.getAttemptId()) || other.getScheduledTime() == null
                    || List.of("CANCELLED","VOIDED","EXPIRED").contains(other.getStatus())) continue;
            int otherDuration = other.getExamSession().getTimeLimitMinutes();
            if (time.isBefore(other.getScheduledTime().plusMinutes(otherDuration))
                    && other.getScheduledTime().isBefore(time.plusMinutes(duration))) {
                throw new ConflictException("Student has another overlapping scheduled attempt", "SCHEDULE_CONFLICT");
            }
        }
        for (ExamAttempt other : attempts.findByExamSession_SessionIdOrderByAssignment_SlotOrderAscAttemptNoAsc(target.getExamSession().getSessionId())) {
            if (other.getAttemptId().equals(target.getAttemptId()) || other.getScheduledTime() == null
                    || List.of("CANCELLED","VOIDED","EXPIRED").contains(other.getStatus())) continue;
            if (time.isBefore(other.getScheduledTime().plusMinutes(duration))
                    && other.getScheduledTime().isBefore(time.plusMinutes(duration))) {
                throw new ConflictException("Exam attempt slot overlaps another student", "SCHEDULE_CONFLICT");
            }
        }
    }

    private void authorize(UUID examId) {
        ExamSession exam = exams.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        if (currentUser.isAdmin()) return;
        UUID lecturerId = currentUser.currentUserId();
        UUID topicId = exam.getTopic() == null ? null : exam.getTopic().getTopicId();
        UUID subjectId = exam.getTopic() == null || exam.getTopic().getSubject() == null ? null : exam.getTopic().getSubject().getSubjectId();
        boolean owner = exam.getCreatedBy() != null && lecturerId.equals(exam.getCreatedBy().getUserId());
        if (!owner && (subjectId == null || !subjects.isLecturerAssigned(subjectId, lecturerId))) {
            throw new org.springframework.security.access.AccessDeniedException("Lecturer is not assigned to this exam");
        }
    }

    private String actorType() { return currentUser.isAdmin() ? "ADMIN" : "LECTURER"; }

    public record SettingsRequest(int maxAttempts, String scorePolicy, boolean avoidRepeatQuestions, int cooldownMinutes,
            OffsetDateTime attemptWindowStart, OffsetDateTime attemptWindowEnd, boolean allowLecturerExtraAttempts,
            boolean countAbandoned, boolean countExpired, long version) { }
}
