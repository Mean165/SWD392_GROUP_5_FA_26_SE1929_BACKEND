package com.swd392.aiviva.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.entity.AppUser;
import java.util.Optional;
import java.time.Instant;
import java.time.OffsetDateTime;

class JwtAuthenticationFilterTest {

    @Test
    void invalidSignatureReturnsUnauthorizedWithoutLeakingDetails() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtService.extractUsername("invalid-token"))
                .thenThrow(new SignatureException("signature implementation detail"));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer error=\"invalid_token\"", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().isEmpty());
        verifyNoInteractions(userRepository, chain);
    }

    @Test
    void inactiveAccountCannotUsePreviouslyIssuedAccessToken() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser inactive = AppUser.builder().isActive(false).build();
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("lecturer@example.test");
        when(userRepository.findByEmail("lecturer@example.test")).thenReturn(Optional.of(inactive));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void passwordResetInvalidatesPreviouslyIssuedAccessTokens() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser user = AppUser.builder().isActive(true)
                .passwordChangedAt(OffsetDateTime.now()).build();
        request.addHeader("Authorization", "Bearer old-token");
        when(jwtService.extractUsername("old-token")).thenReturn("lecturer@example.test");
        when(userRepository.findByEmail("lecturer@example.test")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("old-token", "lecturer@example.test")).thenReturn(true);
        when(jwtService.extractIssuedAt("old-token")).thenReturn(Instant.now().minusSeconds(10));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void temporaryPasswordTokenCannotAccessOtherEndpoints() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setServletPath("/api/users/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser user = AppUser.builder().isActive(true).mustChangePassword(true).build();
        request.addHeader("Authorization", "Bearer temporary-token");
        when(jwtService.extractUsername("temporary-token")).thenReturn("student@example.test");
        when(userRepository.findByEmail("student@example.test")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("temporary-token", "student@example.test")).thenReturn(true);
        when(jwtService.extractUserId("temporary-token")).thenReturn(java.util.UUID.randomUUID());

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void temporaryPasswordTokenMayOnlyCallChangePasswordEndpoint() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AppUser user = AppUser.builder().isActive(true).mustChangePassword(true).build();
        request.addHeader("Authorization", "Bearer temporary-token");
        when(jwtService.extractUsername("temporary-token")).thenReturn("student@example.test");
        when(userRepository.findByEmail("student@example.test")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("temporary-token", "student@example.test")).thenReturn(true);
        when(jwtService.extractUserId("temporary-token")).thenReturn(java.util.UUID.randomUUID());

        filter.doFilter(request, response, chain);

        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }
}
