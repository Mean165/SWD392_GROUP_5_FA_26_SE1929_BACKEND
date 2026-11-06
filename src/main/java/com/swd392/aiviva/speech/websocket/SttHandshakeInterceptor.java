package com.swd392.aiviva.speech.websocket;

import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class SttHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID = "stt.userId";
    public static final String USER_EMAIL = "stt.userEmail";
    public static final String TOKEN = "stt.token";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SttHandshakeInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        String token = bearerToken(request.getHeaders());
        if (token == null) {
            token = queryToken(request.getURI().getRawQuery());
        }
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String email = jwtService.extractUsername(token);
            UUID userId = jwtService.extractUserId(token);
            Instant issuedAt = jwtService.extractIssuedAt(token);
            AppUser user = email == null ? null : userRepository.findByEmail(email).orElse(null);
            if (user == null || userId == null || !jwtService.isTokenValid(token, email)
                    || !userId.equals(user.getUserId()) || Boolean.FALSE.equals(user.getIsActive())
                    || (user.getPasswordChangedAt() != null
                    && (issuedAt == null || issuedAt.isBefore(user.getPasswordChangedAt().toInstant())))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (user.getRole() == null || !"ST".equalsIgnoreCase(user.getRole().getRoleCode())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            if (Boolean.TRUE.equals(user.getMustChangePassword())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(USER_ID, userId);
            attributes.put(USER_EMAIL, email);
            attributes.put(TOKEN, token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
        // No credentials or request payloads are logged here.
    }

    private String bearerToken(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return value != null && value.startsWith("Bearer ") ? value.substring(7) : null;
    }

    private String queryToken(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && "access_token".equals(pair.substring(0, separator))) {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
