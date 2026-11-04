package com.swd392.aiviva.question.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.entity.Rubric;
import com.swd392.aiviva.question.entity.RubricCriteria;
import com.swd392.aiviva.question.entity.Subject;
import com.swd392.aiviva.question.entity.SubjectTopic;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.question.repository.RubricRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RubricResolverTest {
    @Test
    void resolvesTheSameActiveSubjectRubricForQuestionsAtDifferentBloomLevels() {
        QuestionRepository questions = mock(QuestionRepository.class);
        RubricCriteriaRepository criteria = mock(RubricCriteriaRepository.class);
        RubricRepository rubrics = mock(RubricRepository.class);
        UUID subjectId = UUID.randomUUID();
        UUID rubricId = UUID.randomUUID();
        Subject subject = new Subject();
        subject.setSubjectId(subjectId);
        SubjectTopic topic = SubjectTopic.builder().subject(subject).build();
        Rubric rubric = Rubric.builder().rubricId(rubricId).subject(subject).status("ACTIVE").build();
        List<RubricCriteria> rubricCriteria = List.of(
                RubricCriteria.builder().rubric(rubric).criteriaName("Accuracy")
                        .expectedKnowledgePoints("Correct facts").weightRatio(new BigDecimal("1.00")).build());
        when(rubrics.findFirstBySubject_SubjectIdAndStatusOrderByUpdatedAtDesc(subjectId, "ACTIVE"))
                .thenReturn(Optional.of(rubric));
        when(criteria.findByRubric_RubricIdOrderByCriteriaId(rubricId)).thenReturn(rubricCriteria);
        Question remember = Question.builder().questionId(UUID.randomUUID()).topic(topic).bloomLevel("REMEMBER").build();
        Question create = Question.builder().questionId(UUID.randomUUID()).topic(topic).bloomLevel("CREATE").build();
        when(questions.findById(remember.getQuestionId())).thenReturn(Optional.of(remember));
        when(questions.findById(create.getQuestionId())).thenReturn(Optional.of(create));
        RubricResolver resolver = new RubricResolver(questions, criteria, rubrics);

        assertEquals(rubricCriteria, resolver.resolveForQuestion(remember.getQuestionId()));
        assertEquals(rubricCriteria, resolver.resolveForQuestion(create.getQuestionId()));
    }
}
