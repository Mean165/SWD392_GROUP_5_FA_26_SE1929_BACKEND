package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.exam.dto.response.ExamEntryEligibilityResponse;
import com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
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
            Set.of("PENDING", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "SUBMITTED", "ABSENT");

    private final StudentSessionAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public StudentExamAssignmentService(
            StudentSessionAssignmentRepository assignmentRepository, UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
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

        Page<StudentExamAssignmentResponse> result = assignmentRepository.findStudentSchedule(
                currentStudentId(), fromTime, toTime, normalizeStatus(status),
                pageRequest(page, size, Sort.by(Sort.Direction.ASC, "scheduledTime")));
        return pageResponse(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentExamAssignmentResponse> getHistory(int page, int size) {
        Page<StudentExamAssignmentResponse> result = assignmentRepository.findStudentHistory(
                currentStudentId(), pageRequest(page, size, Sort.by(Sort.Direction.DESC, "scheduledTime")));
        return pageResponse(result);
    }

    @Transactional(readOnly = true)
    public StudentExamAssignmentResponse getAssignment(UUID assignmentId) {
        UUID studentId = currentStudentId();
        return assignmentRepository.findStudentScheduleById(assignmentId, studentId)
                .orElseThrow(() -> new com.swd392.aiviva.common.exception.ResourceNotFoundException(
                        "Exam assignment not found."));
    }

    @Transactional(readOnly = true)
    public ExamEntryEligibilityResponse getEligibility(UUID assignmentId) {
        StudentSessionAssignment assignment = findOwnedAssignment(assignmentId);
        return ExamEntryEligibility.evaluate(assignment, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public StudentSessionAssignment findOwnedAssignment(UUID assignmentId) {
        return assignmentRepository.findByAssignmentIdAndStudentUserId(assignmentId, currentStudentId())
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
        if (!ASSIGNMENT_STATUSES.contains(normalized)) {
            throw new BusinessException("Unsupported assignment status.");
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
}
