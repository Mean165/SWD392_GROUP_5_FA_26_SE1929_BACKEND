package com.swd392.aiviva.auth.security;

import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import io.jsonwebtoken.JwtException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String userEmail;
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (JwtException | IllegalArgumentException exception) {
            rejectInvalidToken(response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            AppUser user = userRepository.findByEmail(userEmail).orElse(null);

            if (user == null || Boolean.FALSE.equals(user.getIsActive())
                    || !jwtService.isTokenValid(jwt, userEmail)
                    || tokenPredatesPasswordChange(user, jwt)) {
                rejectInvalidToken(response);
                return;
            }

            if (user != null) {
                String roleCode = (user.getRole() != null) ? user.getRole().getRoleCode() : "ST";
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + roleCode));
                try {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail, null, authorities);
                    authToken.setDetails(new JwtAuthenticationDetails(request, jwtService.extractUserId(jwt)));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (JwtException | IllegalArgumentException exception) {
                    rejectInvalidToken(response);
                    return;
                }

                if (Boolean.TRUE.equals(user.getMustChangePassword())
                        && (!"POST".equalsIgnoreCase(request.getMethod())
                        || !"/api/auth/change-password".equals(request.getServletPath()))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    new ObjectMapper().findAndRegisterModules().writeValue(response.getOutputStream(), ApiResponse.error("Password change is required", "ACCESS_DENIED"));
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void rejectInvalidToken(HttpServletResponse response) {
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            new ObjectMapper().findAndRegisterModules().writeValue(response.getOutputStream(), ApiResponse.error("Bearer token is invalid or expired", "AUTH_INVALID"));
        } catch (IOException ignored) {
            // The response may already be committed by the servlet container.
        }
    }

    private boolean tokenPredatesPasswordChange(AppUser user, String token) {
        if (user.getPasswordChangedAt() == null) return false;
        Instant issuedAt = jwtService.extractIssuedAt(token);
        return issuedAt == null || issuedAt.isBefore(user.getPasswordChangedAt().toInstant());
    }
}
