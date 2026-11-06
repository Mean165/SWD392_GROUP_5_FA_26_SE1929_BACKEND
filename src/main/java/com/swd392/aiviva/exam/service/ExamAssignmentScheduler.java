package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.attempt.service.AttemptEligibilityService;
import com.swd392.aiviva.exam.attempt.entity.ExamAttempt;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.interview.enums.InterviewStatus;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamAssignmentScheduler {
    private final boolean enabled;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamAttemptRepository examAttempts;
    private final AttemptEligibilityService attemptEligibility;
    private final VivaAttemptRepository vivaAttempts;

    public ExamAssignmentScheduler(@Value("${exam.scheduler.enabled:true}") boolean enabled,
                                   StudentSessionAssignmentRepository assignmentRepository,
                                   ExamSessionRepository sessionRepository,
                                   ExamAttemptRepository examAttempts,
                                   AttemptEligibilityService attemptEligibility,
                                   VivaAttemptRepository vivaAttempts) {
        this.enabled = enabled;
        this.assignmentRepository = assignmentRepository;
        this.sessionRepository = sessionRepository;
        this.examAttempts = examAttempts;
        this.attemptEligibility = attemptEligibility;
        this.vivaAttempts = vivaAttempts;
    }

    @Scheduled(fixedDelayString = "${exam.scheduler.delay-ms:60000}")
    @Transactional
    public void markAbsentAndCompleteSessions() {
        if (!enabled) return;
        OffsetDateTime now = OffsetDateTime.now();
        sessionRepository.findAll().forEach(session -> {
            List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId());
            assignments.stream().filter(assignment -> assignment.getRemovedAt() == null).forEach(assignment -> {
                StudentSessionAssignment lockedEnrollment = assignmentRepository.findBySessionAndStudentForUpdate(
                        session.getSessionId(), assignment.getStudent().getUserId()).orElse(null);
                if (lockedEnrollment == null || lockedEnrollment.getRemovedAt() != null) return;
                List<ExamAttempt> attempts = examAttempts.findByAssignment_AssignmentIdOrderByAttemptNoAsc(lockedEnrollment.getAssignmentId());
                for (ExamAttempt candidate : attempts) {
                    ExamAttempt attempt = examAttempts.findForUpdate(lockedEnrollment.getAssignmentId(), candidate.getAttemptNo()).orElse(null);
                    if (attempt == null) continue;
                    if (!"SCHEDULED".equals(attempt.getStatus()) || attempt.getScheduledTime() == null) continue;
                    if (now.isAfter(attempt.getScheduledTime().plusMinutes(session.getTimeLimitMinutes()))) {
                        boolean next = attemptEligibility.markExpired(attempt, now);
                        lockedEnrollment.setStatus(next ? "SCHEDULED" : "ABSENT");
                        assignmentRepository.save(lockedEnrollment);
                    }
                }
                for (ExamAttempt attempt : attempts) {
                    ExamAttempt lockedAttempt = examAttempts.findForUpdate(lockedEnrollment.getAssignmentId(), attempt.getAttemptNo()).orElse(null);
                    if (lockedAttempt == null || !"IN_PROGRESS".equals(lockedAttempt.getStatus()) || lockedAttempt.getStartedAt() == null
                            || now.isBefore(lockedAttempt.getStartedAt().plusMinutes(session.getTimeLimitMinutes()))) continue;
                    VivaAttempt interview = lockedAttempt.getInterviewSessionId() == null ? null
                            : vivaAttempts.findByExamAttemptIdForUpdate(lockedAttempt.getAttemptId()).orElse(null);
                    if (interview != null) {
                        interview.setStatus(InterviewStatus.ABANDONED.name());
                        interview.setEndTime(now);
                        vivaAttempts.save(interview);
                    }
                    boolean next = attemptEligibility.markAbandoned(lockedAttempt, now);
                    lockedEnrollment.setStatus(next ? "SCHEDULED" : "COMPLETED");
                    assignmentRepository.save(lockedEnrollment);
                }
            });
            boolean finished = !assignments.isEmpty() && assignments.stream().allMatch(a -> List.of("COMPLETED", "ABSENT", "CANCELLED").contains(a.getStatus().toUpperCase(Locale.ROOT)));
            if (finished && !"COMPLETED".equalsIgnoreCase(session.getStatus()) && !"CANCELLED".equalsIgnoreCase(session.getStatus())) {
                session.setStatus("COMPLETED");
                sessionRepository.save(session);
            }
        });
    }
}
