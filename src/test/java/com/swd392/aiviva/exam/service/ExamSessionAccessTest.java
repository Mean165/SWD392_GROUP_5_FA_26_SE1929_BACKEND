package com.swd392.aiviva.exam.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.exam.dto.request.UpdateExamSessionRequest;
import com.swd392.aiviva.exam.attempt.service.AttemptRosterService;
import com.swd392.aiviva.exam.attempt.service.ExamAttemptService;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.repository.ExamSessionBloomDistributionRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Subject;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.SubjectRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.question.service.RubricResolver;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ExamSessionAccessTest {
    private final ExamSessionRepository sessions = mock(ExamSessionRepository.class);
    private final StudentSessionAssignmentRepository assignments = mock(StudentSessionAssignmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubjectTopicRepository topics = mock(SubjectTopicRepository.class);
    private final SubjectRepository subjectAssignments = mock(SubjectRepository.class);
    private final ExamSessionBloomDistributionRepository distributions = mock(ExamSessionBloomDistributionRepository.class);
    private final VivaAttemptRepository attempts = mock(VivaAttemptRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final RubricResolver rubrics = mock(RubricResolver.class);
    private final FinalGradeReportRepository grades = mock(FinalGradeReportRepository.class);
    private final UUID lecturerId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void assignedSubjectLecturerCanManageExamCreatedBySomeoneElse() {
        AppUser lecturer = lecturer();
        ExamSession session = session(UUID.randomUUID());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(users.findByEmail(lecturer.getEmail())).thenReturn(Optional.of(lecturer));
        when(subjectAssignments.isLecturerAssigned(subjectId, lecturerId)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> service().updateExamSession(sessionId, UpdateExamSessionRequest.builder().timeLimitMinutes(181).build()));
    }

    @Test
    void unrelatedLecturerStillReceivesAccessDenied() {
        AppUser lecturer = lecturer();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(UUID.randomUUID())));
        when(users.findByEmail(lecturer.getEmail())).thenReturn(Optional.of(lecturer));
        when(subjectAssignments.isLecturerAssigned(subjectId, lecturerId)).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> service().updateExamSession(sessionId, UpdateExamSessionRequest.builder().timeLimitMinutes(181).build()));
    }

    @Test
    void sessionCreatorCanManageExamEvenWhenNotInSubjectAssignmentJson() {
        AppUser lecturer = lecturer();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(lecturerId)));
        when(users.findByEmail(lecturer.getEmail())).thenReturn(Optional.of(lecturer));
        assertThrows(BusinessException.class,
                () -> service().updateExamSession(sessionId, UpdateExamSessionRequest.builder().timeLimitMinutes(181).build()));
    }

    private ExamSessionServiceImpl service() {
        return new ExamSessionServiceImpl(sessions, assignments, users, topics, subjectAssignments,
                distributions, attempts, questions, rubrics, grades, mock(AttemptRosterService.class),
                mock(ExamAttemptService.class), 40);
    }

    private AppUser lecturer() {
        AppUser user = AppUser.builder().userId(lecturerId).email("assigned-lecturer@test.local")
                .role(Role.builder().roleCode("LE").build()).isActive(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_LE"))));
        return user;
    }

    private ExamSession session(UUID creatorId) {
        AppUser creator = AppUser.builder().userId(creatorId).build();
        Subject subject = new Subject();
        subject.setSubjectId(subjectId);
        SubjectTopic topic = SubjectTopic.builder().topicId(UUID.randomUUID()).subject(subject).build();
        return ExamSession.builder().sessionId(sessionId).createdBy(creator).topic(topic).status("SCHEDULED").build();
    }
}
