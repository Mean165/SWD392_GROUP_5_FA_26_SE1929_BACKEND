package com.swd392.aiviva.system.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.system.dto.request.CreateSubjectRequest;
import com.swd392.aiviva.system.dto.request.SubjectTopicInput;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SubjectCatalogServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private UserService users;
    @Mock private UserRepository userRepository;

    private SubjectCatalogService service;

    @BeforeEach
    void setUp() {
        service = new SubjectCatalogService(jdbc, new ObjectMapper(), users, userRepository);
    }

    @Test
    void createRejectsDuplicateSubjectCodeBeforeWriting() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("SWD392"))).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.create(new CreateSubjectRequest(" SWD392 ", "Software Development", null, List.of()),
                        "admin@example.test"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createRejectsDuplicateTopicCodesInSameRequest() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("SWD392"))).thenReturn(false);
        CreateSubjectRequest request = new CreateSubjectRequest("SWD392", "Software Development", null,
                List.of(new SubjectTopicInput("TOPIC-1", "Topic One", null),
                        new SubjectTopicInput("topic-1", "Topic Duplicate", null)));

        assertThrows(BusinessException.class, () -> service.create(request, "admin@example.test"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(userRepository, never()).findByEmail("admin@example.test");
    }
}
