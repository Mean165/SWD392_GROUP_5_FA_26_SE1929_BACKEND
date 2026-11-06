package com.swd392.aiviva.exam.attempt.service;

import com.swd392.aiviva.evaluation.entity.FinalGradeReport;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.exam.attempt.entity.ExamAttemptSettings;
import com.swd392.aiviva.exam.attempt.entity.ExamStudentResult;
import com.swd392.aiviva.exam.attempt.entity.StudentAttemptAllowance;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptSettingsRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamStudentResultRepository;
import com.swd392.aiviva.exam.attempt.repository.StudentAttemptAllowanceRepository;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptRosterService {
    private final ExamAttemptRepository attempts;
    private final ExamAttemptSettingsRepository settings;
    private final StudentAttemptAllowanceRepository allowances;
    private final ExamStudentResultRepository results;
    private final FinalGradeReportRepository grades;

    public AttemptRosterService(ExamAttemptRepository attempts, ExamAttemptSettingsRepository settings,
            StudentAttemptAllowanceRepository allowances, ExamStudentResultRepository results,
            FinalGradeReportRepository grades) {
        this.attempts = attempts;
        this.settings = settings;
        this.allowances = allowances;
        this.results = results;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public List<StudentSessionAssignmentResponse> map(UUID examId, List<StudentSessionAssignment> enrollments) {
        if (enrollments.isEmpty()) return List.of();
        Map<UUID, List<ExamAttempt>> byAssignment = attempts.findByExamSession_SessionIdOrderByAssignment_SlotOrderAscAttemptNoAsc(examId)
                .stream().collect(Collectors.groupingBy(row -> row.getAssignment().getAssignmentId()));
        Map<UUID, StudentAttemptAllowance> allowanceByStudent = allowances.findByExamSession_SessionId(examId)
                .stream().collect(Collectors.toMap(row -> row.getStudent().getUserId(), row -> row, (left, right) -> left));
        Map<UUID, ExamStudentResult> resultByStudent = results.findByExamSession_SessionId(examId)
                .stream().collect(Collectors.toMap(row -> row.getStudent().getUserId(), row -> row, (left, right) -> left));
        ExamAttemptSettings config = settings.findById(examId).orElseGet(() -> {
            ExamAttemptSettings defaults = new ExamAttemptSettings(); defaults.setExamId(examId); return defaults;
        });
        List<UUID> interviewIds = byAssignment.values().stream().flatMap(List::stream)
                .map(ExamAttempt::getInterviewSessionId).filter(java.util.Objects::nonNull).toList();
        Map<UUID, FinalGradeReport> gradeByInterview = interviewIds.isEmpty() ? Map.of()
                : grades.findByAttempt_AttemptIdIn(interviewIds).stream()
                    .collect(Collectors.toMap(row -> row.getAttempt().getAttemptId(), row -> row, (left, right) -> left));
        List<StudentSessionAssignmentResponse> mapped = new ArrayList<>(enrollments.size());
        for (StudentSessionAssignment enrollment : enrollments) {
            List<ExamAttempt> studentAttempts = byAssignment.getOrDefault(enrollment.getAssignmentId(), List.of());
            StudentAttemptAllowance allowance = allowanceByStudent.get(enrollment.getStudent().getUserId());
            ExamStudentResult official = resultByStudent.get(enrollment.getStudent().getUserId());
            int extra = allowance == null ? 0 : allowance.getExtraAttempts();
            int used = (int) studentAttempts.stream().filter(ExamAttempt::isCounted)
                    .filter(attempt -> !"VOIDED".equals(attempt.getStatus())).count();
            int allowed = Math.max(used, config.getMaxAttempts() + extra);
            List<StudentSessionAssignmentResponse.ExamAttemptItem> attemptItems = studentAttempts.stream()
                    .map(attempt -> toItem(attempt, gradeByInterview.get(attempt.getInterviewSessionId()))).toList();
            StudentSessionAssignmentResponse.ExamAttemptItem current = attemptItems.stream()
                    .filter(item -> List.of("IN_PROGRESS", "SCHEDULED").contains(item.getStatus()))
                    .findFirst().orElseGet(() -> attemptItems.isEmpty() ? null : attemptItems.get(attemptItems.size() - 1));
            BigDecimal officialTotal = official == null ? null : official.getComputedTotal();
            if (official != null && official.getOfficialAttemptId() != null) {
                officialTotal = attemptItems.stream().filter(item -> official.getOfficialAttemptId().equals(item.getExamAttemptId()))
                        .map(StudentSessionAssignmentResponse.ExamAttemptItem::getFinalTotal).findFirst().orElse(null);
            }
            mapped.add(StudentSessionAssignmentResponse.builder()
                    .assignmentId(enrollment.getAssignmentId()).sessionId(examId).sessionName(enrollment.getExamSession().getSessionName())
                    .studentId(enrollment.getStudent().getUserId()).studentCode(enrollment.getStudent().getStudentOrStaffCode())
                    .studentName(enrollment.getStudent().getFullName()).studentEmail(enrollment.getStudent().getEmail())
                    .scheduledTime(current == null ? enrollment.getScheduledTime() : current.getScheduledTime())
                    .status(enrollment.getStatus()).slotOrder(current == null ? enrollment.getSlotOrder() : current.getSlotOrder())
                    .cancelledReason(enrollment.getCancelledReason())
                    .attemptId(current == null ? null : current.getInterviewSessionId())
                    .attemptStatus(current == null ? null : current.getStatus())
                    .totalAiScore(current == null ? null : current.getAiSuggestedTotal())
                    .finalTeacherScore(officialTotal)
                    .attemptsUsed(used).attemptsAllowed(allowed).attemptsRemaining(Math.max(0, allowed - used))
                    .extraAttempts(extra).currentAttempt(current).attempts(attemptItems)
                    .removed(enrollment.getRemovedAt() != null)
                    .officialResult(StudentSessionAssignmentResponse.OfficialExamResult.builder()
                            .policy(official == null ? config.getScorePolicy() : official.getPolicyApplied())
                            .officialAttemptId(official == null ? null : official.getOfficialAttemptId())
                            .total(officialTotal).released(official != null && official.isReleased()).build())
                    .build());
        }
        return mapped;
    }

    private StudentSessionAssignmentResponse.ExamAttemptItem toItem(ExamAttempt attempt, FinalGradeReport grade) {
        return StudentSessionAssignmentResponse.ExamAttemptItem.builder().examAttemptId(attempt.getAttemptId())
                .attemptNo(attempt.getAttemptNo()).status(attempt.getStatus()).scheduledTime(attempt.getScheduledTime())
                .slotOrder(attempt.getSlotOrder()).interviewSessionId(attempt.getInterviewSessionId())
                .aiSuggestedTotal(grade == null ? null : grade.getTotalAiScore())
                .finalTotal(grade == null ? null : grade.getFinalTeacherScore()).counted(attempt.isCounted())
                .voided("VOIDED".equals(attempt.getStatus())).build();
    }
}
