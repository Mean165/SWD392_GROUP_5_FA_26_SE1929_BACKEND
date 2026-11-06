package com.swd392.aiviva.question.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.interview.repository.AiRubricAssessmentRepository;
import com.swd392.aiviva.question.dto.request.RubricRequest;
import com.swd392.aiviva.question.dto.response.RubricResponse;
import com.swd392.aiviva.question.entity.Rubric;
import com.swd392.aiviva.question.entity.Subject;
import com.swd392.aiviva.question.repository.QuestionRepository;
import com.swd392.aiviva.question.repository.RubricCriteriaRepository;
import com.swd392.aiviva.question.repository.RubricRepository;
import com.swd392.aiviva.question.repository.SubjectRepository;
import com.swd392.aiviva.question.repository.SubjectTopicRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RubricServiceImplTest {
    private final RubricRepository rubrics = mock(RubricRepository.class);
    private final RubricCriteriaRepository criteria = mock(RubricCriteriaRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final SubjectTopicRepository topics = mock(SubjectTopicRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AiRubricAssessmentRepository assessments = mock(AiRubricAssessmentRepository.class);
    private final RubricResolver resolver = mock(RubricResolver.class);
    private final UUID subjectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Subject subject;
    private AppUser lecturer;
    private RubricServiceImpl service;

    @BeforeEach
    void setUp() {
        subject = new Subject();
        subject.setSubjectId(subjectId);
        lecturer = AppUser.builder().userId(userId).email("lecturer@test.local")
                .role(Role.builder().roleCode("LE").build()).build();
        when(subjects.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subjects.isLecturerAssigned(subjectId, userId)).thenReturn(true);
        when(users.findByEmail("lecturer@test.local")).thenReturn(Optional.of(lecturer));
        when(rubrics.existsBySubject_SubjectIdAndStatus(subjectId, "ACTIVE")).thenReturn(false);
        when(criteria.findByRubric_RubricIdOrderByCriteriaId(any())).thenReturn(List.of());
        when(rubrics.save(any(Rubric.class))).thenAnswer(invocation -> {
            Rubric saved = invocation.getArgument(0);
            if (saved.getRubricId() == null) saved.setRubricId(UUID.randomUUID());
            return saved;
        });
        service = new RubricServiceImpl(rubrics, criteria, subjects, topics, questions, users, assessments, resolver);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "lecturer@test.local", null, List.of()));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void createsSubjectRubricWithoutARequiredBloomField() {
        RubricResponse response = service.createRubric(subjectId, request("0.60", "0.40"));

        assertEquals(subjectId, response.getSubjectId());
        assertEquals("ACTIVE", response.getStatus());
        verify(rubrics).save(any(Rubric.class));
    }

    @Test
    void rejectsSubjectRubricWhenWeightsDoNotTotalOne() {
        assertThrows(BusinessException.class, () -> service.createRubric(subjectId, request("0.40", "0.40")));

        verify(rubrics, never()).save(any(Rubric.class));
    }

    private RubricRequest request(String firstWeight, String secondWeight) {
        return new RubricRequest("Course rubric", "Shared criteria for the whole course.", List.of(
                new RubricRequest.CriterionRequest("Accuracy", "Correct concepts.", new BigDecimal(firstWeight)),
                new RubricRequest.CriterionRequest("Reasoning", "Clear supporting reasoning.", new BigDecimal(secondWeight))));
    }
}
