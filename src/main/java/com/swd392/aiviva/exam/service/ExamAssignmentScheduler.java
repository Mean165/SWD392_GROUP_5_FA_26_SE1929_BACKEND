package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
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
    private final VivaAttemptRepository attemptRepository;

    public ExamAssignmentScheduler(@Value("${exam.scheduler.enabled:true}") boolean enabled,
                                   StudentSessionAssignmentRepository assignmentRepository,
                                   ExamSessionRepository sessionRepository,
                                   VivaAttemptRepository attemptRepository) {
        this.enabled = enabled;
        this.assignmentRepository = assignmentRepository;
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
    }

    @Scheduled(fixedDelayString = "${exam.scheduler.delay-ms:60000}")
    @Transactional
    public void markAbsentAndCompleteSessions() {
        if (!enabled) return;
        OffsetDateTime now = OffsetDateTime.now();
        sessionRepository.findAll().forEach(session -> {
            List<StudentSessionAssignment> assignments = assignmentRepository.findByExamSessionSessionId(session.getSessionId());
            assignments.forEach(assignment -> {
                if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus()) || assignment.getScheduledTime() == null) return;
                boolean hasLiveAttempt = attemptRepository.findByAssignment_AssignmentId(assignment.getAssignmentId())
                        .map(VivaAttempt::getStatus).map(status -> "IN_PROGRESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)).orElse(false);
                if (!hasLiveAttempt && now.isAfter(assignment.getScheduledTime().plusMinutes(session.getTimeLimitMinutes()))) {
                    assignment.setStatus("ABSENT");
                    assignmentRepository.save(assignment);
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
