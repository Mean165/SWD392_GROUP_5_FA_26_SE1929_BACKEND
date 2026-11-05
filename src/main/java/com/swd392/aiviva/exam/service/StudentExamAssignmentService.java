package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.exam.dto.response.ExamEntryEligibilityResponse;
import com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.attempt.service.AttemptRosterService;
import com.swd392.aiviva.exam.attempt.service.AttemptEligibilityService;
import com.swd392.aiviva.exam.attempt.domain.AttemptEligibilityDecision;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentExamAssignmentService {

    private static final Set<String> ASSIGNMENT_STATUSES =
            Set.of("PENDING", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "ABSENT", "CANCELLED");

    private final StudentSessionAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AttemptRosterService attemptRosterService;
    private final AttemptEligibilityService attemptEligibilityService;

    public StudentExamAssignmentService(
            StudentSessionAssignmentRepository assignmentRepository, UserRepository userRepository) {
        this(assignmentRepository, userRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StudentExamAssignmentService(StudentSessionAssignmentRepository assignmentRepository, UserRepository userRepository,
            AttemptRosterService attemptRosterService, AttemptEligibilityService attemptEligibilityService) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.attemptRosterService = attemptRosterService;
        this.attemptEligibilityService = attemptEligibilityService;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentExamAssignmentResponse> getSchedule(
            OffsetDateTime from, OffsetDateTime to, String status, int page, int size) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime fromTime = from != null
                ? from
                : now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime toTime = to != null
                ? to
                : now.toLocalDate().plusDays(30).atTime(23, 59, 59, 999_999_999).atOffset(now.getOffset());
        if (fromTime.isAfter(toTime)) {
            throw new BusinessException("'from' must not be after 'to'.");
        }

        String normalizedStatus = normalizeStatus(status);
        Page<StudentExamAssignmentResponse> result = assignmentRepository.findStudentSchedule(
                currentStudentId(), fromTime, toTime, normalizedStatus, normalizedStatus != null,
                pageRequest(page, size, Sort.by(Sort.Direction.ASC, "scheduledTime")));
        return enrich(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentExamAssignmentResponse> getHistory(int page, int size) {
        Page<StudentExamAssignmentResponse> result = assignmentRepository.findStudentHistory(
                currentStudentId(), pageRequest(page, size, Sort.by(Sort.Direction.DESC, "scheduledTime")));
        return enrich(result);
    }

    @Transactional(readOnly = true)
    public StudentExamAssignmentResponse getAssignment(UUID assignmentId) {
        UUID studentId = currentStudentId();
        StudentExamAssignmentResponse response = assignmentRepository.findStudentScheduleById(assignmentId, studentId)
                .orElseThrow(() -> new com.swd392.aiviva.common.exception.ResourceNotFoundException(
                        "Exam assignment not found."));
        return enrichOne(response, studentId);
    }

    @Transactional(readOnly = true)
    public ExamEntryEligibilityResponse getEligibility(UUID assignmentId, UUID examAttemptId) {
        StudentSessionAssignment assignment = findOwnedAssignment(assignmentId);
        if (attemptEligibilityService != null) {
            AttemptEligibilityDecision decision = attemptEligibilityService.inspect(assignment, examAttemptId, OffsetDateTime.now());
            return ExamEntryEligibilityResponse.builder().canEnter(decision.allowed())
                    .reason(decision.allowed() ? "Interview entry is available." : decision.code())
                    .reasonCode(decision.code()).attemptsUsed(decision.attemptsUsed())
                    .attemptsAllowed(decision.attemptsAllowed()).attemptsRemaining(decision.attemptsRemaining())
                    .nextAttemptId(decision.examAttemptId()).nextAttemptNo(decision.attemptNo())
                    .retryAt(decision.retryAt()).build();
        }
        return ExamEntryEligibility.evaluate(assignment, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public StudentSessionAssignment findOwnedAssignment(UUID assignmentId) {
        return assignmentRepository.findByAssignmentIdAndStudentUserId(assignmentId, currentStudentId())
                .filter(assignment -> assignment.getRemovedAt() == null)
                .orElseThrow(() -> new com.swd392.aiviva.common.exception.ResourceNotFoundException(
                        "Exam assignment not found."));
    }

    private UUID currentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required.");
        }
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated student account was not found."));
        if (Boolean.FALSE.equals(user.getIsActive()) || user.getRole() == null
                || !"ST".equalsIgnoreCase(user.getRole().getRoleCode())) {
            throw new AccessDeniedException("Only an active student can access their exam assignments.");
        }
        return user.getUserId();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) return null;
        if (!ASSIGNMENT_STATUSES.contains(normalized)) {
            throw new com.swd392.aiviva.common.exception.InvalidRequestParameterException(
                    "status", "INVALID_ASSIGNMENT_STATUS", "Unsupported assignment status.");
        }
        return normalized;
    }

    private Pageable pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, sort);
    }

    private PageResponse<StudentExamAssignmentResponse> pageResponse(Page<StudentExamAssignmentResponse> page) {
        return PageResponse.<StudentExamAssignmentResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private PageResponse<StudentExamAssignmentResponse> enrich(Page<StudentExamAssignmentResponse> page) {
        if (attemptRosterService == null || page.isEmpty()) return pageResponse(page);
        var enrollments = assignmentRepository.findAllById(page.getContent().stream().map(StudentExamAssignmentResponse::getAssignmentId).toList());
        java.util.Map<UUID, List<StudentSessionAssignment>> byExam = enrollments.stream()
                .collect(java.util.stream.Collectors.groupingBy(row -> row.getExamSession().getSessionId()));
        java.util.Map<UUID, StudentSessionAssignmentResponse> enriched = new java.util.HashMap<>();
        byExam.forEach((examId, rows) -> attemptRosterService.map(examId, rows)
                .forEach(row -> enriched.put(row.getAssignmentId(), row)));
        List<StudentExamAssignmentResponse> content = page.getContent().stream()
                .map(row -> withAttempts(row, enriched.get(row.getAssignmentId()))).toList();
        return PageResponse.<StudentExamAssignmentResponse>builder().content(content).page(page.getNumber())
                .size(page.getSize()).totalElements(page.getTotalElements()).totalPages(page.getTotalPages()).build();
    }

    private StudentExamAssignmentResponse enrichOne(StudentExamAssignmentResponse response, UUID studentId) {
        if (attemptRosterService == null) return response;
        return assignmentRepository.findById(response.getAssignmentId())
                .filter(row -> row.getStudent().getUserId().equals(studentId))
                .map(row -> withAttempts(response, attemptRosterService.map(row.getExamSession().getSessionId(), java.util.List.of(row)).getFirst()))
                .orElse(response);
    }

    private StudentExamAssignmentResponse withAttempts(StudentExamAssignmentResponse response,
            StudentSessionAssignmentResponse enriched) {
        if (enriched == null) return response;
        response.setAttemptsUsed(enriched.getAttemptsUsed());
        response.setAttemptsAllowed(enriched.getAttemptsAllowed());
        response.setAttemptsRemaining(enriched.getAttemptsRemaining());
        response.setExtraAttempts(enriched.getExtraAttempts());
        response.setAttempts(enriched.getAttempts());
        response.setCurrentAttempt(enriched.getCurrentAttempt());
        response.setOfficialResult(enriched.getOfficialResult());
        if (response.getOfficialResult() != null && !response.getOfficialResult().isReleased()) {
            response.getOfficialResult().setOfficialAttemptId(null);
            response.getOfficialResult().setTotal(null);
            response.setFinalTeacherScore(null);
        }
        if (response.getAttempts() != null) {
            UUID selected = response.getOfficialResult() == null ? null : response.getOfficialResult().getOfficialAttemptId();
            boolean released = response.getOfficialResult() != null && response.getOfficialResult().isReleased();
            response.setAttempts(response.getAttempts().stream().map(item -> StudentSessionAssignmentResponse.ExamAttemptItem.builder()
                    .examAttemptId(item.getExamAttemptId()).attemptNo(item.getAttemptNo()).status(item.getStatus())
                    .scheduledTime(item.getScheduledTime()).slotOrder(item.getSlotOrder()).interviewSessionId(item.getInterviewSessionId())
                    .aiSuggestedTotal(null)
                    .finalTotal(released && "AVERAGE".equals(response.getOfficialResult().getPolicy()) && !item.isVoided()
                            ? item.getFinalTotal()
                            : released && selected != null && selected.equals(item.getExamAttemptId()) ? item.getFinalTotal() : null)
                    .counted(item.isCounted()).voided(item.isVoided()).build()).toList());
        }
        response.setAttemptId(enriched.getAttemptId());
        response.setAttemptStatus(enriched.getAttemptStatus());
        response.setTotalAiScore(null);
        response.setScheduledTime(enriched.getScheduledTime());
        response.setSlotOrder(enriched.getSlotOrder());
        return response;
    }
}
