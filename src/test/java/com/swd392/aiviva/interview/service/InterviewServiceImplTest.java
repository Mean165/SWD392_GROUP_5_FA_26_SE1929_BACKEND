package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.common.exception.AttemptExpiredException;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.dto.AnswerAnalysisResult;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.dto.response.SubmitAnswerResponse;
import com.swd392.aiviva.interview.entity.InteractionTurn;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.AcousticFluencyMetricRepository;
import com.swd392.aiviva.interview.enums.InterviewStatus;
import com.swd392.aiviva.interview.repository.AiRubricAssessmentRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    private final UUID studentId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID turnId = UUID.randomUUID();

    @Mock
    private VivaAttemptRepository vivaAttemptRepository;
    @Mock
    private InteractionTurnRepository interactionTurnRepository;
    @Mock
    private StudentSessionAssignmentRepository assignmentRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private RubricCriteriaRepository rubricCriteriaRepository;
    @Mock
    private AiRubricAssessmentRepository rubricAssessmentRepository;
    @Mock
    private AcousticFluencyMetricRepository acousticFluencyMetricRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TextToSpeechService ttsService;
    @Mock
    private SpeechToTextService sttService;
    @Mock
    private AnswerAnalysisService answerAnalysisService;
    @Mock
    private InterviewAudioStorageService audioStorageService;

    private AppUser student;
    private StudentSessionAssignment assignment;
    private VivaAttempt attempt;
    private InteractionTurn turn;
    private InterviewServiceImpl service;
    private List<InteractionTurn> persistedTurns;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().roleCode("ST").build();
        student = AppUser.builder().userId(studentId).email("student@example.test")
                .role(role).isActive(true).build();
        ExamSession session = ExamSession.builder()
                .sessionId(UUID.randomUUID())
                .status("IN_PROGRESS")
                .startTime(OffsetDateTime.now().minusHours(1))
                .timeLimitMinutes(60)
                .maxMainQuestions(1)
                .maxFollowupPerQuestion(1)
                .build();
        assignment = StudentSessionAssignment.builder()
                .assignmentId(UUID.randomUUID())
                .student(student)
                .examSession(session)
                .status("IN_PROGRESS")
                .scheduledTime(OffsetDateTime.now().minusMinutes(1))
                .build();
        attempt = VivaAttempt.builder()
                .attemptId(attemptId)
                .assignment(assignment)
                .status(InterviewStatus.IN_PROGRESS.name())
                .startTime(OffsetDateTime.now().minusMinutes(1))
                .build();
        Question question = Question.builder()
                .questionId(UUID.randomUUID())
                .questionText("Explain the core concept.")
                .build();
        turn = InteractionTurn.builder()
                .turnId(turnId)
                .attempt(attempt)
                .mainQuestion(question)
                .turnOrder(1)
                .turnType("MAIN")
                .aiQuestionText(question.getQuestionText())
                .createdAt(OffsetDateTime.now().minusSeconds(30))
                .build();
        persistedTurns = new ArrayList<>(List.of(turn));
        service = new InterviewServiceImpl(
                vivaAttemptRepository,
                interactionTurnRepository,
                assignmentRepository,
                questionRepository,
                rubricCriteriaRepository,
                rubricAssessmentRepository,
                acousticFluencyMetricRepository,
                userRepository,
                ttsService,
                sttService,
                answerAnalysisService,
                new InterviewConfig(),
                audioStorageService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@example.test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ST"))));
        lenient().when(userRepository.findByEmail("student@example.test")).thenReturn(Optional.of(student));
        lenient().when(vivaAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        lenient().when(interactionTurnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId))
                .thenAnswer(invocation -> new ArrayList<>(persistedTurns));
        lenient().when(interactionTurnRepository.findById(turnId)).thenReturn(Optional.of(turn));
        lenient().when(interactionTurnRepository.save(any(InteractionTurn.class)))
                .thenAnswer(invocation -> {
                    InteractionTurn saved = invocation.getArgument(0);
                    if (!persistedTurns.contains(saved)) {
                        if (saved.getTurnId() == null) {
                            saved.setTurnId(UUID.randomUUID());
                        }
                        persistedTurns.add(saved);
                    }
                    return saved;
                });
        lenient().when(vivaAttemptRepository.save(any(VivaAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(rubricAssessmentRepository.findByTurn_TurnId(turnId)).thenReturn(List.of());
        lenient().when(rubricCriteriaRepository.findByQuestion_QuestionId(turn.getMainQuestion().getQuestionId()))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void finalizesOnceAndReturnsThePersistedResultOnRetry() {
        when(answerAnalysisService.analyzeAnswer(any())).thenReturn(new AnswerAnalysisResult(
                AnswerAnalysisResult.Classification.SUFFICIENT,
                "The answer addresses the question.",
                List.of(),
                null,
                List.of()));
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .attemptId(attemptId)
                .turnId(turnId)
                .transcriptText("A relevant answer.")
                .build();

        SubmitAnswerResponse first = service.submitAnswer(request);
        SubmitAnswerResponse retry = service.submitAnswer(request);

        assertTrue(first.isCompleted());
        assertEquals(first.getTranscriptText(), retry.getTranscriptText());
        assertEquals("SUFFICIENT", retry.getAnswerClassification());
        verify(answerAnalysisService, times(1)).analyzeAnswer(any());
        verify(interactionTurnRepository, times(1)).save(turn);
    }

    @Test
    void doesNotCreateFollowUpsAfterTheConfiguredLimit() {
        assignment.getSession().setMaxFollowupPerQuestion(0);
        when(answerAnalysisService.analyzeAnswer(any())).thenReturn(new AnswerAnalysisResult(
                AnswerAnalysisResult.Classification.PARTIAL,
                "A key point is missing.",
                List.of("key point"),
                "Can you clarify the relevant point?",
                List.of()));

        SubmitAnswerResponse response = service.submitAnswer(SubmitAnswerRequest.builder()
                .attemptId(attemptId)
                .turnId(turnId)
                .transcriptText("A partial answer.")
                .build());

        assertTrue(response.isCompleted());
        assertTrue(!response.isFollowUpGenerated());
        verify(interactionTurnRepository, times(1)).save(turn);
        verify(questionRepository, never()).findByApprovalStatusOrderByCreatedAtAsc(anyString());
    }

    @Test
    void createsOneContextualFollowUpAndDoesNotDuplicateItOnRetry() {
        when(answerAnalysisService.analyzeAnswer(any())).thenReturn(new AnswerAnalysisResult(
                AnswerAnalysisResult.Classification.PARTIAL,
                "The answer omits a limitation.",
                List.of("one limitation"),
                "What limitation would apply in this case?",
                List.of()));
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .attemptId(attemptId)
                .turnId(turnId)
                .transcriptText("A relevant but incomplete answer.")
                .build();

        SubmitAnswerResponse first = service.submitAnswer(request);
        SubmitAnswerResponse retry = service.submitAnswer(request);

        assertTrue(first.isFollowUpGenerated());
        assertEquals("FOLLOW_UP", first.getNextTurn().getTurnType());
        assertTrue(retry.isFollowUpGenerated());
        assertEquals(2, persistedTurns.size());
        verify(answerAnalysisService, times(1)).analyzeAnswer(any());
    }

    @Test
    void closesAndRejectsAnAnswerPastItsDeadline() {
        turn.setCreatedAt(OffsetDateTime.now().minusSeconds(181));

        assertThrows(AttemptExpiredException.class, () -> service.submitAnswer(SubmitAnswerRequest.builder()
                .attemptId(attemptId)
                .turnId(turnId)
                .transcriptText("Late answer.")
                .build()));

        assertEquals(InterviewStatus.COMPLETED.name(), attempt.getStatus());
        assertEquals("COMPLETED", assignment.getStatus());
        verify(answerAnalysisService, never()).analyzeAnswer(any());
    }

    @Test
    void deniesAnotherStudentAccessToAttemptTurns() {
        UUID otherId = UUID.randomUUID();
        AppUser otherStudent = AppUser.builder().userId(otherId).email("other@example.test")
                .role(Role.builder().roleCode("ST").build()).isActive(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("other@example.test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ST"))));
        when(userRepository.findByEmail("other@example.test")).thenReturn(Optional.of(otherStudent));
        when(vivaAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThrows(AccessDeniedException.class, () -> service.getInterviewTurns(attemptId));
    }
}
