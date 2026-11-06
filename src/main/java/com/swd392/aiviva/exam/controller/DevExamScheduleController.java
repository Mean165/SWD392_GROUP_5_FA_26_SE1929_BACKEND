package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/dev/exam-sessions")
public class DevExamScheduleController {
    private final boolean enabled;
    private final ExamSessionRepository sessionRepository;
    private final StudentSessionAssignmentRepository assignmentRepository;

    public DevExamScheduleController(@Value("${exam.dev.shift-enabled:false}") boolean enabled,
                                     ExamSessionRepository sessionRepository,
                                     StudentSessionAssignmentRepository assignmentRepository) {
        this.enabled = enabled; this.sessionRepository = sessionRepository; this.assignmentRepository = assignmentRepository;
    }

    @PostMapping("/{id}/shift-to-now")
    @PreAuthorize("hasAnyRole('AD','LE')")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> shift(@PathVariable UUID id) {
        if (!enabled) throw new BusinessException("Development schedule shifting is disabled");
        var session = sessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam session not found"));
        OffsetDateTime start = OffsetDateTime.now().plusMinutes(5); int i = 0;
        for (var a : assignmentRepository.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(id)) { a.setScheduledTime(start.plusMinutes((long)i++ * (session.getTimeLimitMinutes()+session.getBufferMinutes()))); a.setSlotOrder(i); a.setStatus("SCHEDULED"); assignmentRepository.save(a); }
        session.setStartTime(start); session.setStatus("IN_PROGRESS"); sessionRepository.save(session);
        return ResponseEntity.ok(ApiResponse.success("Development schedule shifted", Map.of("sessionId", id, "startTime", start, "assignments", i)));
    }
}
