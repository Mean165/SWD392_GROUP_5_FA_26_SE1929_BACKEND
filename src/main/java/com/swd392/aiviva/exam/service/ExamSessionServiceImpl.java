package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.exam.dto.request.CreateExamSessionRequest;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.dto.response.ExamSessionResponse;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
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
    private final UserRepository userRepository;

    public ExamSessionServiceImpl(ExamSessionRepository examSessionRepository,
                                   UserRepository userRepository) {
        this.examSessionRepository = examSessionRepository;
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
}
