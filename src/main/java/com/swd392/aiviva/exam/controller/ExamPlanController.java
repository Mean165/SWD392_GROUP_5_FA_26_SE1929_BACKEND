package com.swd392.aiviva.exam.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.dto.response.AttemptQuestionPlanResponse;
import com.swd392.aiviva.interview.entity.AttemptQuestionPlan;
import com.swd392.aiviva.interview.repository.AttemptQuestionPlanRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.interview.service.QuestionPlanService;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.entity.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exam-sessions")
@PreAuthorize("hasAnyRole('AD','LE')")
public class ExamPlanController {
    private final VivaAttemptRepository attemptRepository;
    private final QuestionPlanService planService;
    private final StudentSessionAssignmentRepository assignmentRepository;
    private final AttemptQuestionPlanRepository planRepository;
    private final ExamSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public ExamPlanController(VivaAttemptRepository attemptRepository, QuestionPlanService planService,
                              StudentSessionAssignmentRepository assignmentRepository, AttemptQuestionPlanRepository planRepository,
                              ExamSessionRepository sessionRepository, UserRepository userRepository) {
        this.attemptRepository = attemptRepository;
        this.planService = planService;
        this.assignmentRepository = assignmentRepository;
        this.planRepository = planRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/{sessionId}/attempts/{attemptId}/plan")
    public ResponseEntity<ApiResponse<AttemptQuestionPlanResponse>> plan(@PathVariable UUID sessionId, @PathVariable UUID attemptId) {
        authorize(sessionId);
        var attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new com.swd392.aiviva.common.exception.ResourceNotFoundException("Attempt not found"));
        if (!sessionId.equals(attempt.getAssignment().getExamSession().getSessionId())) throw new com.swd392.aiviva.common.exception.ResourceNotFoundException("Attempt not found in session");
        List<AttemptQuestionPlan> rows = planService.getPlan(attemptId);
        return ResponseEntity.ok(ApiResponse.success("Question plan retrieved", AttemptQuestionPlanResponse.builder().attemptId(attemptId).totalQuestions(rows.size()).questions(rows.stream().map(p -> AttemptQuestionPlanResponse.Item.builder().planId(p.getPlanId()).position(p.getPosition()).questionId(p.getQuestion().getQuestionId()).questionText(p.getQuestion().getQuestionText()).bloomLevel(p.getBloomLevel().name()).createdAt(p.getCreatedAt()).build()).toList()).build()));
    }

    @GetMapping("/{sessionId}/question-usage")
    public ResponseEntity<ApiResponse<Map<String,Object>>> usage(@PathVariable UUID sessionId) {
        authorize(sessionId);
        var assignments = assignmentRepository.findByExamSessionSessionIdOrderBySlotOrderAscScheduledTimeAsc(sessionId);
        Map<UUID, Long> counts = assignments.stream().flatMap(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).stream()).flatMap(a -> planRepository.findByAttempt_AttemptIdOrderByPositionAsc(a.getAttemptId()).stream()).collect(Collectors.groupingBy(p -> p.getQuestion().getQuestionId(), Collectors.counting()));
        int adjacent = 0;
        List<java.util.Set<UUID>> sets = assignments.stream().map(a -> attemptRepository.findByAssignment_AssignmentId(a.getAssignmentId()).map(at -> planRepository.findByAttempt_AttemptIdOrderByPositionAsc(at.getAttemptId()).stream().map(p -> p.getQuestion().getQuestionId()).collect(Collectors.toSet())).orElse(java.util.Set.of())).toList();
        for (int i = 1; i < sets.size(); i++) { java.util.Set<UUID> overlap = new java.util.HashSet<>(sets.get(i - 1)); overlap.retainAll(sets.get(i)); if (!overlap.isEmpty()) adjacent++; }
        return ResponseEntity.ok(ApiResponse.success("Question usage retrieved", Map.of("sessionId", sessionId, "usage", counts, "adjacentRepeatPairs", adjacent)));
    }

    private void authorize(UUID sessionId) {
        var session = sessionRepository.findById(sessionId).orElseThrow(() -> new com.swd392.aiviva.common.exception.ResourceNotFoundException("Exam session not found"));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        AppUser user = userRepository.findByEmail(auth.getName()).orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (user.getRole() != null && "LE".equalsIgnoreCase(user.getRole().getRoleCode()) && (session.getCreatedBy() == null || !user.getUserId().equals(session.getCreatedBy().getUserId()))) throw new AccessDeniedException("Lecturers can only access sessions they created");
    }
}
