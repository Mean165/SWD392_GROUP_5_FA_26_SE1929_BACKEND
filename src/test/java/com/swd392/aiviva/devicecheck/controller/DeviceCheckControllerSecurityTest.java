package com.swd392.aiviva.devicecheck.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.aiviva.auth.security.JwtAuthenticationDetails;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.auth.security.SecurityConfig;
import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.devicecheck.dto.response.DeviceCheckConfigResponse;
import com.swd392.aiviva.devicecheck.service.DeviceCheckService;
import com.swd392.aiviva.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceCheckController.class)
@Import(SecurityConfig.class)
class DeviceCheckControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceCheckService deviceCheckService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/student/device-check/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lecturerRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/student/device-check/config").with(user("lecturer").roles("LE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/student/device-check/config").with(user("admin").roles("AD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentRoleCanReadConfig() throws Exception {
        DeviceCheckConfigResponse config = DeviceCheckConfigResponse.builder()
                .languages(List.of("vi", "en"))
                .defaultLanguage("vi")
                .acceptedMimeTypes(List.of("audio/wav"))
                .maxAudioBytes(2 * 1024 * 1024)
                .maxDurationSeconds(15)
                .recommendedRecordingSeconds(5)
                .sampleSentences(Map.of("vi", "Tôi sẵn sàng bắt đầu bài thi vấn đáp", "en", "sample"))
                .build();
        when(deviceCheckService.getConfig()).thenReturn(config);

        mockMvc.perform(get("/api/student/device-check/config")
                        .with(authentication(studentAuthentication())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.data.sampleSentences.vi").value("Tôi sẵn sàng bắt đầu bài thi vấn đáp"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Tôi sẵn sàng bắt đầu bài thi vấn đáp")));
    }

    @Test
    void ttsReturnsAudioAndNoStoreHeaderForStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(deviceCheckService.synthesizeTest(studentId, "vi")).thenReturn(new byte[] {1, 2});

        mockMvc.perform(get("/api/student/device-check/tts")
                        .with(authentication(studentAuthentication(studentId))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[] {1, 2}));
    }

    @Test
    void rateLimitedRequestReturns429AndRetryAfterHeader() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(deviceCheckService.synthesizeTest(studentId, "vi"))
                .thenThrow(new RateLimitExceededException(32));

        mockMvc.perform(get("/api/student/device-check/tts")
                        .with(authentication(studentAuthentication(studentId))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "32"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void ttsProviderFailureDoesNotExposeInternalError() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(deviceCheckService.synthesizeTest(studentId, "vi"))
                .thenThrow(new com.swd392.aiviva.common.exception.ExternalServiceException(
                        "Device-check text-to-speech provider failed.",
                        org.springframework.http.HttpStatus.BAD_GATEWAY));

        mockMvc.perform(get("/api/student/device-check/tts")
                        .with(authentication(studentAuthentication(studentId))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Device-check text-to-speech provider failed."));
    }

    @Test
    void sttAcceptsMultipartFileAndLanguageForStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.wav", "audio/wav", new byte[] {1, 2});
        MockMultipartFile language = new MockMultipartFile(
                "language", "", "text/plain", "en".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(deviceCheckService.transcribe(
                org.mockito.ArgumentMatchers.eq(studentId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("en")))
                .thenReturn(com.swd392.aiviva.devicecheck.dto.response.DeviceCheckSttResponse.builder()
                        .transcript("recognized")
                        .matchScore(0.85)
                        .level("GOOD")
                        .durationMs(1_000)
                        .language("en")
                        .suggestion("Micro hoạt động tốt")
                        .build());

        mockMvc.perform(multipart("/api/student/device-check/stt")
                        .file(file)
                        .file(language)
                        .with(authentication(studentAuthentication(studentId))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.data.transcript").value("recognized"))
                .andExpect(jsonPath("$.data.matchScore").value(0.85))
                .andExpect(jsonPath("$.data.level").value("GOOD"))
                .andExpect(jsonPath("$.data.durationMs").value(1000))
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.suggestion").value("Micro hoạt động tốt"));
    }

    private UsernamePasswordAuthenticationToken studentAuthentication() {
        return studentAuthentication(UUID.randomUUID());
    }

    private UsernamePasswordAuthenticationToken studentAuthentication(UUID studentId) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "student", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ST")));
        authentication.setDetails(new JwtAuthenticationDetails(new MockHttpServletRequest(), studentId));
        return authentication;
    }
}
