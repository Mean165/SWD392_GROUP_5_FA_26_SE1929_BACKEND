package com.swd392.aiviva.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.exam.dto.response.ExamReadinessIssue;
import com.swd392.aiviva.exam.dto.response.ExamReadinessResponse;
import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.ExamSessionBloomDistribution;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionBloomDistributionRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.evaluation.repository.FinalGradeReportRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.Subject;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.question.service.RubricResolver;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ExamReadinessTest {
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID SUBJECT_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private final ExamSessionRepository sessionRepository = mock(ExamSessionRepository.class);
    private final StudentSessionAssignmentRepository assignmentRepository = mock(StudentSessionAssignmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubjectTopicRepository topicRepository = mock(SubjectTopicRepository.class);
    private final ExamSessionBloomDistributionRepository distributionRepository = mock(ExamSessionBloomDistributionRepository.class);
    private final VivaAttemptRepository attemptRepository = mock(VivaAttemptRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final RubricResolver rubricResolver = mock(RubricResolver.class);
    private final FinalGradeReportRepository gradeRepository = mock(FinalGradeReportRepository.class);

    private ExamSession session;
    private AppUser owner;
    private ExamSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        owner = AppUser.builder().userId(OWNER_ID).email("lecturer@test.local")
                .role(Role.builder().roleCode("LE").build()).build();
        Subject subject = new Subject();
        subject.setSubjectId(SUBJECT_ID);
        SubjectTopic topic = SubjectTopic.builder().topicId(UUID.randomUUID()).subject(subject).createdBy(owner).build();
        session = ExamSession.builder().sessionId(SESSION_ID).createdBy(owner).topic(topic)
                .maxMainQuestions(6).maxFollowupPerQuestion(1).timeLimitMinutes(20)
                .maxOverlapPercent(40).avoidRepeatWindow(2).status("SCHEDULED").build();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("lecturer@test.local")).thenReturn(Optional.of(owner));
        when(assignmentRepository.findByExamSessionSessionId(SESSION_ID)).thenReturn(List.of());
        when(distributionRepository.findBySession_SessionId(SESSION_ID)).thenReturn(allBloomDistribution(1));
        when(questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(SUBJECT_ID, "APPROVED"))
                .thenReturn(questions(1));
        when(rubricResolver.hasValidRubric(SUBJECT_ID)).thenReturn(true);
        when(rubricResolver.activeRubric(SUBJECT_ID)).thenReturn(Optional.of(
                com.swd392.aiviva.question.entity.Rubric.builder().build()));
        service = new ExamSessionServiceImpl(sessionRepository, assignmentRepository, userRepository, topicRepository,
                distributionRepository, attemptRepository, questionRepository, rubricResolver, gradeRepository, 40);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "lecturer@test.local", null, List.of()));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void reportsReadyWhenRubricAndApprovedBloomPoolsCanServeEveryStudent() {
        when(assignmentRepository.findByExamSessionSessionId(SESSION_ID)).thenReturn(assignments(2));
        when(questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(SUBJECT_ID, "APPROVED"))
                .thenReturn(questions(2));

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        assertTrue(response.isReady());
        assertTrue(response.getIssues().isEmpty());
    }

    @Test
    void reportsMissingSubjectRubricWithoutAttachingRubricToBloom() {
        when(rubricResolver.hasValidRubric(SUBJECT_ID)).thenReturn(false);
        when(rubricResolver.activeRubric(SUBJECT_ID)).thenReturn(Optional.empty());

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        assertFalse(response.isReady());
        assertIssue(response, "SUBJECT_RUBRIC_MISSING");
    }

    @Test
    void reportsExistingRubricWithInvalidWeightsSeparately() {
        when(rubricResolver.activeRubric(SUBJECT_ID)).thenReturn(Optional.of(
                com.swd392.aiviva.question.entity.Rubric.builder().build()));
        when(rubricResolver.hasValidRubric(SUBJECT_ID)).thenReturn(false);

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        assertIssue(response, "SUBJECT_RUBRIC_INVALID");
    }

    @Test
    void reportsOnlyTheBloomPoolThatHasTooFewApprovedQuestions() {
        when(distributionRepository.findBySession_SessionId(SESSION_ID)).thenReturn(distribution(BloomLevel.REMEMBER, 2));
        when(questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(SUBJECT_ID, "APPROVED"))
                .thenReturn(List.of(question(BloomLevel.REMEMBER)));

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        ExamReadinessIssue issue = response.getIssues().stream()
                .filter(item -> "INSUFFICIENT_QUESTIONS".equals(item.getCode())).findFirst().orElseThrow();
        assertEquals("REMEMBER", issue.getBloomLevel());
        assertEquals(1L, issue.getHave());
        assertEquals(2L, issue.getNeed());
    }

    @Test
    void reportsWhenPairwiseQuestionOverlapExceedsConfiguredLimit() {
        when(assignmentRepository.findByExamSessionSessionId(SESSION_ID)).thenReturn(assignments(2));

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        ExamReadinessIssue issue = response.getIssues().stream()
                .filter(item -> "OVERLAP_EXCEEDS_LIMIT".equals(item.getCode())).findFirst().orElseThrow();
        assertEquals(40, issue.getMaxPercent());
        assertTrue(issue.getMinimumAdditionalQuestions() > 0);
        assertTrue(issue.getAdditionalQuestionsByBloom().size() > 0);
    }

    @Test
    void allowsTheFlooredFortyPercentOverlapForSixMainQuestions() {
        when(assignmentRepository.findByExamSessionSessionId(SESSION_ID)).thenReturn(assignments(2));
        List<Question> pool = new ArrayList<>();
        pool.add(question(BloomLevel.REMEMBER));
        pool.add(question(BloomLevel.UNDERSTAND));
        for (BloomLevel level : List.of(BloomLevel.APPLY, BloomLevel.ANALYZE, BloomLevel.EVALUATE, BloomLevel.CREATE)) {
            pool.add(question(level));
            pool.add(question(level));
        }
        when(questionRepository.findByTopic_Subject_SubjectIdAndApprovalStatusOrderByCreatedAtAsc(SUBJECT_ID, "APPROVED"))
                .thenReturn(pool);

        ExamReadinessResponse response = service.readiness(SESSION_ID);

        assertTrue(response.isReady());
        assertEquals(6, session.getMaxMainQuestions());
        assertEquals(40, session.getMaxOverlapPercent());
    }

    @Test
    void checksOnlyRubricAndPerBloomMinimumWhenThereAreNoStudents() {
        ExamReadinessResponse response = service.readiness(SESSION_ID);

        assertTrue(response.isReady());
        assertEquals(0, response.getStudentCount());
    }

    private List<ExamSessionBloomDistribution> allBloomDistribution(int count) {
        return java.util.Arrays.stream(BloomLevel.values()).map(level -> ExamSessionBloomDistribution.builder()
                .session(session).bloomLevel(level).questionCount(count).build()).toList();
    }

    private List<ExamSessionBloomDistribution> distribution(BloomLevel level, int count) {
        return List.of(ExamSessionBloomDistribution.builder().session(session).bloomLevel(level).questionCount(count).build());
    }

    private List<Question> questions(int countPerBloom) {
        List<Question> questions = new ArrayList<>();
        for (BloomLevel level : BloomLevel.values()) for (int i = 0; i < countPerBloom; i++) questions.add(question(level));
        return questions;
    }

    private Question question(BloomLevel level) {
        return Question.builder().questionId(UUID.randomUUID()).bloomLevel(level.name()).approvalStatus("APPROVED").build();
    }

    private List<StudentSessionAssignment> assignments(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> StudentSessionAssignment.builder().assignmentId(UUID.randomUUID())
                        .examSession(session).status("PENDING").build()).toList();
    }

    private void assertIssue(ExamReadinessResponse response, String code) {
        assertTrue(response.getIssues().stream().anyMatch(issue -> code.equals(issue.getCode())));
    }
}
