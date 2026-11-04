package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoExamScheduleInitializer {
    private static final UUID DEMO_SESSION = UUID.fromString("42f2583a-9040-5984-9a79-1171c7d8d553");
    private final boolean enabled;
    private final ExamSessionRepository sessionRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;

    public DemoExamScheduleInitializer(@Value("${app.seed.demo-schedule:false}") boolean enabled,
                                       ExamSessionRepository sessionRepository,
                                       StudentSessionAssignmentRepository assignmentRepository) {
        this.enabled = enabled; this.sessionRepository = sessionRepository; this.assignmentRepository = assignmentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void shiftDemoSchedule() {
        if (!enabled) return;
        var session = sessionRepository.findById(DEMO_SESSION).orElse(null);
        if (session == null) return;
        OffsetDateTime start = OffsetDateTime.now().plusMinutes(5);
        session.setStartTime(start); session.setStatus("IN_PROGRESS"); sessionRepository.save(session);
        int slot = 1;
        for (var assignment : assignmentRepository.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(DEMO_SESSION)) {
            assignment.setSlotOrder(slot); assignment.setScheduledTime(start.plusMinutes((long) (slot - 1) * (session.getTimeLimitMinutes() + session.getBufferMinutes()))); assignment.setStatus("SCHEDULED"); assignmentRepository.save(assignment); slot++;
        }
    }
}
