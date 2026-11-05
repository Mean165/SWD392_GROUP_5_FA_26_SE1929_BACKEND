
package com.swd392.aiviva.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, HttpStatus.UNAUTHORIZED, "Authentication is required", "AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(
                                "/api/monitoring/health",
                                "/api/system/status",
                                "/"
                        ).permitAll()
                        // JWT is checked in the WebSocket handshake interceptor (browser clients use a query token).
                        .requestMatchers("/ws/stt").permitAll()
                        .requestMatchers("/api/system/admin/**").hasRole("AD")
                        .requestMatchers(
                                "/api/student/**",
                                "/api/interviews/**"
                        ).hasRole("ST")
                        .requestMatchers("/api/users/**").hasRole("AD")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/question-generation/**",
                                "/api/topics/**",
                                "/api/questions/**",
                                "/api/rubrics/**",
                                "/api/exams/**",
                                "/api/evaluations/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers("/api/v1/grading/**").hasAnyRole("AD", "LE")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/question-generation/**",
                                "/api/topics/**",
                                "/api/questions/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/question-generation/**",
                                "/api/questions/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/question-generation/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reports/students/**"
                        ).hasAnyRole("ST", "AD", "LE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reports/lecturers/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/exam-sessions/*/students"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers("/api/exam-sessions/**")
                        .hasAnyRole("AD", "LE")
                        .requestMatchers(
                                "/api/topics/**",
                                "/api/questions/**",
                                "/api/rubrics/**"
                        ).hasAnyRole("AD", "LE")
                        .requestMatchers(
                                "/api/exams/**",
                                "/api/evaluations/**"
                        ).hasAnyRole("ST", "AD", "LE")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService jwtOnlyUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("Username/password authentication is disabled"); };
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().findAndRegisterModules().writeValue(response.getOutputStream(), ApiResponse.error(message, code));
    }
}
