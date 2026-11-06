package com.swd392.aiviva.speech.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.repository.UserRepository;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class SttHandshakeInterceptorTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SttHandshakeInterceptor interceptor = new SttHandshakeInterceptor(jwtService, userRepository);
    private final UUID userId = UUID.randomUUID();
    private final String email = "student@example.test";

    @BeforeEach
    void setUp() {
        AppUser user = AppUser.builder()
                .userId(userId)
                .email(email)
                .role(Role.builder().roleCode("ST").build())
                .isActive(true)
                .mustChangePassword(false)
                .passwordChangedAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(jwtService.extractUsername("valid-token")).thenReturn(email);
        when(jwtService.extractUserId("valid-token")).thenReturn(userId);
        when(jwtService.extractIssuedAt("valid-token")).thenReturn(OffsetDateTime.now().toInstant());
        when(jwtService.isTokenValid("valid-token", email)).thenReturn(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

    @Test
    void acceptsStudentJwtAtHandshake() {
        var request = request("valid-token");
        var response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes));
        assertEquals(userId, attributes.get(SttHandshakeInterceptor.USER_ID));
        assertEquals(email, attributes.get(SttHandshakeInterceptor.USER_EMAIL));
    }

    @Test
    void rejectsInvalidTokenBeforeUpgrade() {
        var request = request("bad-token");
        var response = mock(ServerHttpResponse.class);
        when(jwtService.extractUsername("bad-token")).thenThrow(new IllegalArgumentException("invalid"));

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>()));
        org.mockito.ArgumentCaptor<HttpStatus> status = org.mockito.ArgumentCaptor.forClass(HttpStatus.class);
        org.mockito.Mockito.verify(response).setStatusCode(status.capture());
        assertEquals(HttpStatus.UNAUTHORIZED, status.getValue());
    }

    @Test
    void rejectsNonStudentAtHandshake() {
        AppUser lecturer = AppUser.builder()
                .userId(userId)
                .email(email)
                .role(Role.builder().roleCode("LE").build())
                .isActive(true)
                .mustChangePassword(false)
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(lecturer));
        var request = request("valid-token");
        var response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>()));
        org.mockito.ArgumentCaptor<HttpStatus> status = org.mockito.ArgumentCaptor.forClass(HttpStatus.class);
        org.mockito.Mockito.verify(response).setStatusCode(status.capture());
        assertEquals(HttpStatus.FORBIDDEN, status.getValue());
    }

    private ServerHttpRequest request(String token) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/stt?access_token=" + token));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        return request;
    }
}
