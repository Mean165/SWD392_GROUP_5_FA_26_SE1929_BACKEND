package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.exam.dto.request.AssignStudentSessionRequest;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.dto.response.StudentSessionAssignmentResponse;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamSessionServiceImpl implements ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final StudentSessionAssignmentRepository studentSessionAssignmentRepository;
    private final UserRepository userRepository;

    public ExamSessionServiceImpl(ExamSessionRepository examSessionRepository,
                                   StudentSessionAssignmentRepository studentSessionAssignmentRepository,
                                   UserRepository userRepository) {
        this.examSessionRepository = examSessionRepository;
        this.studentSessionAssignmentRepository = studentSessionAssignmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ExamSessionResponse createExamSession(CreateExamSessionRequest request) {
        AppUser currentUser = getAuthenticatedUser();
        validateAdminOrLecturer(currentUser);

        AppUser creator = currentUser;
        if (request.getCreatedBy() != null) {
            String roleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
            if ("AD".equalsIgnoreCase(roleCode)) {
                creator = userRepository.findById(request.getCreatedBy())
                        .orElseThrow(() -> new ResourceNotFoundException("Creator user with id " + request.getCreatedBy() + " not found"));
            } else if (!currentUser.getUserId().equals(request.getCreatedBy())) {
                throw new AccessDeniedException("Lecturers can only create exam sessions under their own account");
            }
        }

        String status = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().trim().toUpperCase()
                : "SCHEDULED";

        ExamSession examSession = ExamSession.builder()
                .createdBy(creator)
                .sessionName(request.getSessionName().trim())
                .maxMainQuestions(request.getMaxMainQuestions())
                .maxFollowupPerQuestion(request.getMaxFollowupPerQuestion())
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .status(status)
                .startTime(request.getStartTime())
                .createdAt(OffsetDateTime.now())
                .build();

        ExamSession saved = examSessionRepository.save(examSession);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ExamSessionResponse updateExamSession(UUID id, UpdateExamSessionRequest request) {
        AppUser currentUser = getAuthenticatedUser();
        validateAdminOrLecturer(currentUser);

        ExamSession session = examSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + id));

        String roleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
        if ("LE".equalsIgnoreCase(roleCode) && session.getCreatedBy() != null
                && !session.getCreatedBy().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Lecturers can only update exam sessions they created");
        }

        if (request.getSessionName() != null && !request.getSessionName().isBlank()) {
            session.setSessionName(request.getSessionName().trim());
        }
        if (request.getMaxMainQuestions() != null) {
            session.setMaxMainQuestions(request.getMaxMainQuestions());
        }
        if (request.getMaxFollowupPerQuestion() != null) {
            session.setMaxFollowupPerQuestion(request.getMaxFollowupPerQuestion());
        }
        if (request.getTimeLimitMinutes() != null) {
            session.setTimeLimitMinutes(request.getTimeLimitMinutes());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            session.setStatus(request.getStatus().trim().toUpperCase());
        }
        if (request.getStartTime() != null) {
            session.setStartTime(request.getStartTime());
        }

        ExamSession updated = examSessionRepository.save(session);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamSessionResponse> getAllExamSessions() {
        return examSessionRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSessionResponse getExamSessionById(UUID id) {
        ExamSession examSession = examSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + id));
        return mapToResponse(examSession);
    }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse assignStudentToSession(AssignStudentSessionRequest request) {
        if (request.getSessionId() == null) {
            throw new BusinessException("Session ID is required");
        }
        return assignStudentToSession(request.getSessionId(), request);
    }

    @Override
    @Transactional
    public StudentSessionAssignmentResponse assignStudentToSession(UUID sessionId, AssignStudentSessionRequest request) {
        AppUser currentUser = getAuthenticatedUser();
        validateAdminOrLecturer(currentUser);

        ExamSession examSession = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session not found with id: " + sessionId));

        String roleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
        if ("LE".equalsIgnoreCase(roleCode) && examSession.getCreatedBy() != null
                && !examSession.getCreatedBy().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Lecturers can only assign students to exam sessions they created");
        }

        if (request.getStudentCode() == null || request.getStudentCode().trim().isBlank()) {
            throw new BusinessException("Student code is required");
        }

        AppUser student = userRepository.findByStudentOrStaffCode(request.getStudentCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with code: " + request.getStudentCode().trim()));

        if (Boolean.FALSE.equals(student.getIsActive())) {
            throw new BusinessException("Student account with code " + request.getStudentCode() + " is inactive");
        }

        if (studentSessionAssignmentRepository.existsByExamSessionSessionIdAndStudentUserId(sessionId, student.getUserId())) {
            throw new BusinessException("Student " + request.getStudentCode() + " is already assigned to this exam session");
        }

        OffsetDateTime scheduledTime = request.getScheduledTime() != null
                ? request.getScheduledTime()
                : (examSession.getStartTime() != null ? examSession.getStartTime() : OffsetDateTime.now());

        String status = (request.getStatus() != null && !request.getStatus().isBlank())
                ? request.getStatus().trim().toUpperCase()
                : "SCHEDULED";

        StudentSessionAssignment assignment = StudentSessionAssignment.builder()
                .examSession(examSession)
                .student(student)
                .scheduledTime(scheduledTime)
                .status(status)
                .build();

        StudentSessionAssignment saved = studentSessionAssignmentRepository.save(assignment);
        return mapToAssignmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSessionAssignmentResponse> getAssignmentsBySessionId(UUID sessionId) {
        return studentSessionAssignmentRepository.findByExamSessionSessionId(sessionId).stream()
                .map(this::mapToAssignmentResponse)
                .toList();
    }

    private AppUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Authenticated user not found"));
    }

    private void validateAdminOrLecturer(AppUser user) {
        String roleCode = user.getRole() != null ? user.getRole().getRoleCode() : "";
        boolean isAuthorized = "AD".equalsIgnoreCase(roleCode) || "LE".equalsIgnoreCase(roleCode);
        if (!isAuthorized) {
            throw new AccessDeniedException("Access denied: Only users with AD (Admin) or LE (Lecturer) role can perform this action");
        }
    }

    private ExamSessionResponse mapToResponse(ExamSession session) {
        return ExamSessionResponse.builder()
                .sessionId(session.getSessionId())
                .createdById(session.getCreatedBy() != null ? session.getCreatedBy().getUserId() : null)
                .createdByName(session.getCreatedBy() != null ? session.getCreatedBy().getFullName() : null)
                .sessionName(session.getSessionName())
                .maxMainQuestions(session.getMaxMainQuestions())
                .maxFollowupPerQuestion(session.getMaxFollowupPerQuestion())
                .timeLimitMinutes(session.getTimeLimitMinutes())
                .status(session.getStatus())
                .startTime(session.getStartTime())
                .createdAt(session.getCreatedAt())
                .build();
    }

    private StudentSessionAssignmentResponse mapToAssignmentResponse(StudentSessionAssignment assignment) {
        return StudentSessionAssignmentResponse.builder()
                .assignmentId(assignment.getAssignmentId())
                .sessionId(assignment.getExamSession() != null ? assignment.getExamSession().getSessionId() : null)
                .sessionName(assignment.getExamSession() != null ? assignment.getExamSession().getSessionName() : null)
                .studentId(assignment.getStudent() != null ? assignment.getStudent().getUserId() : null)
                .studentCode(assignment.getStudent() != null ? assignment.getStudent().getStudentOrStaffCode() : null)
                .studentName(assignment.getStudent() != null ? assignment.getStudent().getFullName() : null)
                .studentEmail(assignment.getStudent() != null ? assignment.getStudent().getEmail() : null)
                .scheduledTime(assignment.getScheduledTime())
                .status(assignment.getStatus())
                .build();
    }
}
