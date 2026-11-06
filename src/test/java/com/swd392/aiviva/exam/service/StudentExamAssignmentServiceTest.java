package com.swd392.aiviva.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.common.exception.InvalidRequestParameterException;
import com.swd392.aiviva.exam.dto.response.StudentExamAssignmentResponse;
import com.swd392.aiviva.exam.repository.StudentSessionAssignmentRepository;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class StudentExamAssignmentServiceTest {
    private final StudentSessionAssignmentRepository assignments = mock(StudentSessionAssignmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UUID studentId = UUID.randomUUID();
    private StudentExamAssignmentService service;

    @BeforeEach
    void setUp() {
        AppUser student = AppUser.builder().userId(studentId).email("student@test.local")
                .role(Role.builder().roleCode("ST").build()).isActive(true).build();
        when(users.findByEmail("student@test.local")).thenReturn(Optional.of(student));
        when(assignments.findStudentSchedule(eq(studentId), any(OffsetDateTime.class), any(OffsetDateTime.class),
                nullable(String.class), org.mockito.ArgumentMatchers.anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        service = new StudentExamAssignmentService(assignments, users);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "student@test.local", null, List.of()));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void returnsAnEmptyPageWhenStudentHasNoCalendar() {
        PageResponse<StudentExamAssignmentResponse> page = service.getSchedule(null, null, "", 0, 20);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(assignments).findStudentSchedule(eq(studentId), any(OffsetDateTime.class), any(OffsetDateTime.class),
                status.capture(), org.mockito.ArgumentMatchers.eq(false), any(Pageable.class));
        assertNull(status.getValue());
    }

    @Test
    void treatsAllAsNoFilterAndNormalizesValidStatus() {
        service.getSchedule(null, null, "ALL", 0, 20);
        ArgumentCaptor<String> all = ArgumentCaptor.forClass(String.class);
        verify(assignments).findStudentSchedule(eq(studentId), any(OffsetDateTime.class), any(OffsetDateTime.class),
                all.capture(), org.mockito.ArgumentMatchers.eq(false), any(Pageable.class));
        assertNull(all.getValue());

        service.getSchedule(null, null, " scheduled ", 0, 20);
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> filterByStatus = ArgumentCaptor.forClass(Boolean.class);
        verify(assignments, org.mockito.Mockito.times(2)).findStudentSchedule(eq(studentId),
                any(OffsetDateTime.class), any(OffsetDateTime.class), status.capture(), filterByStatus.capture(), any(Pageable.class));
        assertEquals("SCHEDULED", status.getAllValues().get(1));
        assertEquals(List.of(false, true), filterByStatus.getAllValues());
    }

    @Test
    void keepsScheduleProjectionWhenSubjectRubricOrTopicNameIsAbsent() {
        StudentExamAssignmentResponse projection = new StudentExamAssignmentResponse(UUID.randomUUID(), null,
                "SCHEDULED", UUID.randomUUID(), "Session", "SCHEDULED", 20, 6, 1,
                null, null, null, 1);
        when(assignments.findStudentSchedule(eq(studentId), any(OffsetDateTime.class), any(OffsetDateTime.class),
                nullable(String.class), org.mockito.ArgumentMatchers.anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(projection)));

        PageResponse<StudentExamAssignmentResponse> page = service.getSchedule(null, null, "ALL", 0, 20);

        assertEquals(1, page.getTotalElements());
        assertNull(page.getContent().getFirst().getTopicName());
    }

    @Test
    void rejectsUnknownStatusWithClearParameterCode() {
        org.junit.jupiter.api.Assertions.assertEquals("INVALID_ASSIGNMENT_STATUS",
                org.junit.jupiter.api.Assertions.assertThrows(InvalidRequestParameterException.class,
                        () -> service.getSchedule(null, null, "NOT_A_STATUS", 0, 20)).getErrorCode());
    }
}
