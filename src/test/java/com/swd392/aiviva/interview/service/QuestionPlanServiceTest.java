package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.attempt.repository.ExamAttemptRepository;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.exam.repository.ExamSessionBloomDistributionRepository;
import com.swd392.aiviva.exam.repository.ExamSessionRepository;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.interview.entity.AttemptQuestionPlan;
import com.swd392.aiviva.interview.entity.InteractionTurn;
import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.interview.repository.AttemptQuestionPlanRepository;
import com.swd392.aiviva.interview.repository.InteractionTurnRepository;
import com.swd392.aiviva.interview.repository.VivaAttemptRepository;
import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.service.RubricResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionPlanServiceTest {

    @Mock private AttemptQuestionPlanRepository planRepository;
    @Mock private ExamSessionRepository sessionRepository;
    @Mock private ExamSessionBloomDistributionRepository distributionRepository;
    @Mock private StudentSessionAssignmentRepository assignmentRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private InteractionTurnRepository turnRepository;
    @Mock private VivaAttemptRepository attemptRepository;
    @Mock private RubricResolver rubricResolver;

    private final UUID attemptId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID topicId = UUID.randomUUID();
    private ExamSession session;
    private StudentSessionAssignment assignment;
    private VivaAttempt attempt;
    private QuestionPlanService service;

    @BeforeEach
    void setUp() {
        SubjectTopic topic = SubjectTopic.builder().topicId(topicId).topicCode("JAVA").topicName("Java").build();
        session = ExamSession.builder().sessionId(sessionId).topic(topic).maxMainQuestions(1)
                .avoidRepeatWindow(0).build();
        assignment = StudentSessionAssignment.builder().assignmentId(UUID.randomUUID())
                .examSession(session).build();
        attempt = VivaAttempt.builder().attemptId(attemptId).assignment(assignment).build();
        service = new QuestionPlanService(planRepository, sessionRepository, distributionRepository,
                assignmentRepository, questionRepository, turnRepository, rubricResolver, attemptRepository, null,
                org.mockito.Mockito.mock(ExamAttemptRepository.class));
        when(turnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId)).thenReturn(List.of());
        when(rubricResolver.resolveForQuestion(any())).thenReturn(List.of());
    }

    @Test
    void doesNotSelectGenericSeedPlaceholderQuestions() {
        Question placeholder = question("Câu hỏi bổ sung mức UNDERSTAND cho chủ đề Java.", "UNDERSTAND");
        Question real = question("Giải thích cách lớp và đối tượng hoạt động trong Java.", "UNDERSTAND");
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(distributionRepository.findBySession_SessionId(sessionId)).thenReturn(List.of());
        when(planRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attemptId)).thenReturn(List.of());
        when(questionRepository.findByTopic_TopicIdAndApprovalStatusOrderByCreatedAtAsc(topicId, "APPROVED"))
                .thenReturn(List.of(placeholder, real));

        List<AttemptQuestionPlan> plan = service.generatePlan(attempt);

        assertEquals(1, plan.size());
        assertEquals(real.getQuestionId(), plan.get(0).getQuestion().getQuestionId());
        assertFalse(plan.get(0).getQuestion().getQuestionText().startsWith("Câu hỏi bổ sung mức "));
    }

    @Test
    void replacesAnUnansweredPlaceholderInAnExistingPlanAndTurn() {
        Question placeholder = question("Câu hỏi bổ sung mức UNDERSTAND cho chủ đề Java.", "UNDERSTAND");
        Question real = question("Giải thích cách lớp và đối tượng hoạt động trong Java.", "UNDERSTAND");
        InteractionTurn turn = InteractionTurn.builder().turnId(UUID.randomUUID()).attempt(attempt)
                .turnType("MAIN").turnOrder(1).mainQuestion(placeholder)
                .aiQuestionText(placeholder.getQuestionText()).build();
        AttemptQuestionPlan row = AttemptQuestionPlan.builder().planId(UUID.randomUUID()).attempt(attempt)
                .question(placeholder).position(1).bloomLevel(BloomLevel.UNDERSTAND).build();
        when(planRepository.findByAttempt_AttemptIdOrderByPositionAsc(attemptId)).thenReturn(List.of(row));
        when(turnRepository.findByAttempt_AttemptIdOrderByTurnOrderAsc(attemptId)).thenReturn(List.of(turn));
        when(questionRepository.findByTopic_TopicIdAndApprovalStatusOrderByCreatedAtAsc(topicId, "APPROVED"))
                .thenReturn(List.of(placeholder, real));

        List<AttemptQuestionPlan> plan = service.generatePlan(attempt);

        assertEquals(real.getQuestionId(), plan.get(0).getQuestion().getQuestionId());
        assertEquals(real.getQuestionText(), turn.getAiQuestionText());
        assertEquals(real.getQuestionId(), turn.getMainQuestion().getQuestionId());
    }

    private Question question(String text, String bloomLevel) {
        return Question.builder().questionId(UUID.randomUUID())
                .topic(SubjectTopic.builder().topicId(topicId).topicCode("JAVA").topicName("Java").build())
                .questionText(text).bloomLevel(bloomLevel).approvalStatus("APPROVED").build();
    }
}
