package com.swd392.aiviva.question.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.auth.security.SecurityConfig;
import com.swd392.aiviva.question.application.QuestionWorkflowService;
import com.swd392.aiviva.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionGenerationController.class)
@Import(SecurityConfig.class)
class QuestionGenerationControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private QuestionWorkflowService workflow;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;

    @Test
    void generationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/question-generation/subjects")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCallGenerationEndpoints() throws Exception {
        mockMvc.perform(get("/api/question-generation/subjects").with(user("student").roles("ST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerCanCallAssignedSubjectListing() throws Exception {
        when(workflow.subjects()).thenReturn(List.of());
        mockMvc.perform(get("/api/question-generation/subjects").with(user("lecturer").roles("LE")))
                .andExpect(status().isOk());
    }
}
