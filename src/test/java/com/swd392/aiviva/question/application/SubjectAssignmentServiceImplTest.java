package com.swd392.aiviva.question.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.question.dto.response.SubjectAssignmentRecord;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import com.swd392.aiviva.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class SubjectAssignmentServiceImplTest {
    private final UUID subjectId = UUID.randomUUID();
    private final UUID assignedLecturer = UUID.randomUUID();
    private final UUID newLecturer = UUID.randomUUID();

    @Mock private JdbcTemplate jdbc;
    @Mock private UserService users;

    private SubjectAssignmentServiceImpl service;
    private SubjectAssignmentRecord current;

    @BeforeEach
    void setUp() {
        service = new SubjectAssignmentServiceImpl(jdbc, new ObjectMapper(), users);
        current = new SubjectAssignmentRecord(subjectId, "SWD392", "Software Development Project",
                List.of(assignedLecturer));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void addingLecturersIsIdempotentAndDoesNotDuplicateExistingAssignments() {
        doReturn(List.of(current)).when(jdbc).query(anyString(), any(RowMapper.class), eq(subjectId));
        when(users.getLecturer(assignedLecturer)).thenReturn(summary(assignedLecturer));
        when(users.getLecturer(newLecturer)).thenReturn(summary(newLecturer));

        SubjectAssignmentRecord updated = service.addLecturers(subjectId, List.of(assignedLecturer, newLecturer));

        assertEquals(List.of(assignedLecturer, newLecturer), updated.lecturerIds());
        verify(users).getLecturer(assignedLecturer);
        verify(users).getLecturer(newLecturer);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void refusesAssignmentsToUnknownLecturers() {
        doReturn(List.of(current)).when(jdbc).query(anyString(), any(RowMapper.class), eq(subjectId));
        when(users.getLecturer(newLecturer)).thenThrow(new ResourceNotFoundException("Lecturer not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.addLecturers(subjectId, List.of(newLecturer)));
        verify(jdbc, never()).update(anyString(), any(), any());
    }

    private LecturerSummaryResponse summary(UUID id) {
        return new LecturerSummaryResponse(id, "Lecturer", "lecturer@example.test", "LE1", true);
    }
}
