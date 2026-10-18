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
}
