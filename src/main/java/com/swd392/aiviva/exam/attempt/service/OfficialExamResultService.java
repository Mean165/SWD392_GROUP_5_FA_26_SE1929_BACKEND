package com.swd392.aiviva.exam.attempt.service;

import com.swd392.aiviva.common.exception.ConflictException;
import com.swd392.aiviva.evaluation.entity.FinalGradeReport;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.evaluation.grading.domain.GradingScoreCalculator;
import com.swd392.aiviva.exam.attempt.domain.AttemptStatus;
import com.swd392.aiviva.exam.attempt.domain.ScorePolicy;
import com.swd392.aiviva.exam.attempt.domain.OfficialScorePolicyCalculator;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.entity.ExamStudentResult;
import com.swd392.aiviva.exam.attempt.repository.AttemptAuditRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamStudentResultRepository;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.user.entity.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class OfficialExamResultService {
    private final ExamAttemptRepository attempts;
    private final ExamAttemptSettingsRepository settings;
    private final ExamStudentResultRepository results;
    private final FinalGradeReportRepository grades;
    private final AttemptAuditRepository audit;
    private final JdbcTemplate jdbc;

    public OfficialExamResultService(ExamAttemptRepository attempts, ExamAttemptSettingsRepository settings,
            ExamStudentResultRepository results, FinalGradeReportRepository grades, AttemptAuditRepository audit,
            JdbcTemplate jdbc) {
        this.attempts = attempts;
        this.settings = settings;
        this.results = results;
        this.grades = grades;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public ExamStudentResult recompute(UUID examId, UUID studentId, UUID actorId, String actorType, String cause) {
        ExamAttemptSettings configuration = settings.findById(examId).orElseGet(() -> {
            ExamAttemptSettings defaults = new ExamAttemptSettings(); defaults.setExamId(examId); return defaults;
        });
        ScorePolicy policy = ScorePolicy.valueOf(configuration.getScorePolicy());
        ExamStudentResult result = results.findForUpdateByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .orElseGet(() -> newResult(examId, studentId, policy));
        UUID oldAttempt = result.getOfficialAttemptId();
        BigDecimal oldComputed = result.getComputedTotal();
        result.setPolicyApplied(policy.name());
        if (policy == ScorePolicy.LECTURER_CHOOSES) {
            ExamAttempt selected = result.getDecidedBy() == null || oldAttempt == null ? null : eligibleFinalized(examId, studentId).stream()
                    .filter(attempt -> oldAttempt.equals(attempt.getAttemptId())).findFirst().orElse(null);
            if (selected == null) {
                result.setOfficialAttemptId(null);
                result.setComputedTotal(null);
                result.setDecidedBy(null);
                result.setDecidedAt(null);
            }
        } else {
            List<GradedAttempt> finalized = gradedAttempts(examId, studentId);
            BigDecimal step = jdbc.queryForObject("select coalesce((select rounding_step from grading_exam_settings where exam_id=?),0.25)",
                    BigDecimal.class, examId);
            OfficialScorePolicyCalculator.Selection selection = OfficialScorePolicyCalculator.select(policy,
                    finalized.stream().map(row -> new OfficialScorePolicyCalculator.FinalizedAttemptScore(
                            row.attempt().getAttemptId(), row.attempt().getAttemptNo(), row.grade().getFinalTeacherScore())).toList(), step);
            result.setOfficialAttemptId(selection.officialAttemptId());
            result.setComputedTotal(selection.computedTotal());
            result.setDecidedBy(null);
            result.setDecidedAt(null);
        }
        ExamStudentResult saved = results.save(result);
        if (!java.util.Objects.equals(oldAttempt, saved.getOfficialAttemptId())
                || !java.util.Objects.equals(oldComputed, saved.getComputedTotal())) {
            saved.setReleased(false);
            saved.setReleasedAt(null);
            saved = results.save(saved);
            audit.append(examId, studentId, actorId, actorType, "OFFICIAL_RESULT_RECOMPUTED", null,
                    java.util.Map.of("policy", policy.name(), "cause", cause == null ? "" : cause,
                            "officialAttemptId", saved.getOfficialAttemptId() == null ? "" : saved.getOfficialAttemptId(),
                            "computedTotal", saved.getComputedTotal() == null ? "" : saved.getComputedTotal()));
        }
        return saved;
    }

    @Transactional
    public ExamStudentResult markReleased(UUID examId, UUID studentId, UUID actorId, String actorType) {
        ExamStudentResult result = results.findForUpdateByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .orElseThrow(() -> new ConflictException("No official result exists to release", "NOT_FINALIZED"));
        if (result.getOfficialAttemptId() == null && result.getComputedTotal() == null) {
            return result;
        }
        if ("AVERAGE".equals(result.getPolicyApplied())) {
            Boolean allFinalizedReleased = jdbc.queryForObject("""
                    select not exists(
                      select 1 from exam_attempts ea
                      join viva_attempt va on va.exam_attempt_id=ea.attempt_id
                      join final_grade_report fg on fg.attempt_id=va.attempt_id and fg.final_teacher_score is not null
                      left join grading_submissions gs on gs.exam_attempt_id=ea.attempt_id
                      where ea.exam_id=? and ea.student_id=? and ea.status='COMPLETED' and ea.counted=true
                        and gs.status is distinct from 'RELEASED'
                    )
                    """, Boolean.class, examId, studentId);
            if (!Boolean.TRUE.equals(allFinalizedReleased)) return result;
        } else if (result.getOfficialAttemptId() != null) {
            Boolean selectedReleased = jdbc.queryForObject(
                    "select exists(select 1 from grading_submissions where exam_attempt_id=? and status='RELEASED')",
                    Boolean.class, result.getOfficialAttemptId());
            if (!Boolean.TRUE.equals(selectedReleased)) return result;
        }
        if (!result.isReleased()) {
            result.setReleased(true);
            result.setReleasedAt(OffsetDateTime.now());
            result = results.save(result);
            audit.append(examId, studentId, actorId, actorType, "OFFICIAL_RESULT_RELEASED", null,
                    java.util.Map.of("officialAttemptId", result.getOfficialAttemptId() == null ? "" : result.getOfficialAttemptId(),
                            "computedTotal", result.getComputedTotal() == null ? "" : result.getComputedTotal()));
        }
        return result;
    }

    @Transactional
    public ExamStudentResult choose(UUID examId, UUID studentId, UUID examAttemptId, boolean confirm, AppUser lecturer) {
        if (!confirm) throw new ConflictException("Explicit confirmation is required to select the official attempt", "CONFIRMATION_REQUIRED");
        ExamAttemptSettings configuration = settings.findById(examId).orElseGet(() -> {
            ExamAttemptSettings defaults = new ExamAttemptSettings(); defaults.setExamId(examId); return defaults;
        });
        if (!ScorePolicy.LECTURER_CHOOSES.name().equals(configuration.getScorePolicy())) {
            throw new ConflictException("Manual official-attempt selection is only available for LECTURER_CHOOSES", "POLICY_NOT_MANUAL");
        }
        ExamAttempt selected = eligibleFinalized(examId, studentId).stream()
                .filter(attempt -> examAttemptId.equals(attempt.getAttemptId())).findFirst()
                .orElseThrow(() -> new ConflictException("Selected attempt has no finalized grade or is voided", "ATTEMPT_NOT_FINALIZED"));
        ExamStudentResult result = results.findForUpdateByExamSession_SessionIdAndStudent_UserId(examId, studentId)
                .orElseGet(() -> newResult(examId, studentId, ScorePolicy.LECTURER_CHOOSES));
        UUID prior = result.getOfficialAttemptId();
        result.setOfficialAttemptId(selected.getAttemptId());
        result.setComputedTotal(null);
        result.setDecidedBy(lecturer);
        result.setDecidedAt(OffsetDateTime.now());
        result.setPolicyApplied(ScorePolicy.LECTURER_CHOOSES.name());
        boolean alreadyReleased = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from grading_submissions where exam_attempt_id=? and status='RELEASED')",
                Boolean.class, selected.getAttemptId()));
        result.setReleased(alreadyReleased);
        result.setReleasedAt(alreadyReleased ? OffsetDateTime.now() : null);
        ExamStudentResult saved = results.save(result);
        audit.append(examId, studentId, lecturer.getUserId(), "LECTURER", "OFFICIAL_ATTEMPT_SELECTED", null,
                java.util.Map.of("previousAttemptId", prior == null ? "" : prior,
                        "officialAttemptId", selected.getAttemptId(), "confirm", true));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> eligibleFinalized(UUID examId, UUID studentId) {
        return gradedAttempts(examId, studentId).stream().map(GradedAttempt::attempt).toList();
    }

    private List<GradedAttempt> gradedAttempts(UUID examId, UUID studentId) {
        return attempts.findByAssignment_ExamSession_SessionIdAndStudent_UserIdOrderByAttemptNoAsc(examId, studentId)
                .stream().filter(ExamAttempt::isCounted)
                .filter(attempt -> attempt.getStatus().equals(AttemptStatus.COMPLETED.name()))
                .filter(attempt -> !"VOIDED".equals(attempt.getStatus()))
                .filter(attempt -> attempt.getInterviewSessionId() != null)
                .map(attempt -> grades.findByAttempt_AttemptId(attempt.getInterviewSessionId())
                        .filter(grade -> grade.getFinalTeacherScore() != null)
                        .map(grade -> new GradedAttempt(attempt, grade)).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    private ExamStudentResult newResult(UUID examId, UUID studentId, ScorePolicy policy) {
        ExamStudentResult result = new ExamStudentResult();
        ExamSession session = new ExamSession(); session.setSessionId(examId);
        AppUser student = new AppUser(); student.setUserId(studentId);
        result.setResultId(UUID.randomUUID());
        result.setExamSession(session);
        result.setStudent(student);
        result.setPolicyApplied(policy.name());
        return result;
    }

    private record GradedAttempt(ExamAttempt attempt, FinalGradeReport grade) { }
}
