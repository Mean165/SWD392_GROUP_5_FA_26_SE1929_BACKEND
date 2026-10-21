package com.swd392.aiviva.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.request.ChangePasswordRequest;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthServiceImpl service = new AuthServiceImpl(users, roles, passwordEncoder, jwtService, userMapper);

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(users, roles, passwordEncoder, jwtService, userMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void temporaryPasswordLoginRequiresPasswordChange() {
        AppUser user = AppUser.builder().userId(java.util.UUID.randomUUID()).email("lecturer@example.test")
                .fullName("Lecturer").studentOrStaffCode("LE1").role(Role.builder().roleCode("LE").build())
                .passwordHash("temporary-hash").passwordChangedAt(OffsetDateTime.now().minusSeconds(1))
                .mustChangePassword(true).isActive(true).build();
        when(users.findByEmailOrStudentOrStaffCode("lecturer@example.test", "lecturer@example.test"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "temporary-hash")).thenReturn(true);
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.eq(user.getEmail()),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn("temporary-access-token");
        when(jwtService.getExpirationTime()).thenReturn(86400L);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("123456");
        var response = service.login(request);

        assertTrue(response.isMustChangePassword());
        assertEquals("temporary-access-token", response.getAccessToken());
    }

    @Test
    void firstPasswordChangeClearsFlagAndReturnsANewSession() {
        AppUser user = AppUser.builder().userId(java.util.UUID.randomUUID()).email("student@example.test")
                .fullName("Student").role(Role.builder().roleCode("ST").build())
                .passwordHash("temporary-hash").mustChangePassword(true).isActive(true).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user.getEmail(), null, java.util.List.of()));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewPassword-123", "temporary-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword-123")).thenReturn("new-password-hash");
        when(users.save(user)).thenReturn(user);
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.eq(user.getEmail()),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn("new-access-token");
        when(jwtService.getExpirationTime()).thenReturn(86400L);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setNewPassword("NewPassword-123");

        var response = service.changePassword(request);

        assertFalse(user.getMustChangePassword());
        assertEquals("new-password-hash", user.getPasswordHash());
        assertTrue(user.getPasswordChangedAt() != null);
        assertFalse(response.isMustChangePassword());
        assertEquals("new-access-token", response.getAccessToken());
    }

    @Test
    void inactiveAccountCannotLogIn() {
        LoginRequest request = new LoginRequest();
        request.setEmail("lecturer@example.test");
        request.setPassword("password123");
        AppUser inactive = AppUser.builder().email("lecturer@example.test")
                .passwordHash("hash").isActive(false).build();
        when(users.findByEmailOrStudentOrStaffCode("lecturer@example.test", "lecturer@example.test"))
                .thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.login(request));
        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void refreshRejectsExpiredOrInvalidTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");
        when(jwtService.extractEmail("expired-token")).thenReturn("lecturer@example.test");
        when(jwtService.isTokenValid("expired-token", "lecturer@example.test")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.refreshToken(request));
        verify(users, never()).findByEmail("lecturer@example.test");
    }

    @Test
    void refreshRejectsTokensIssuedBeforePasswordReset() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-token");
        AppUser user = AppUser.builder().userId(java.util.UUID.randomUUID()).email("lecturer@example.test")
                .role(Role.builder().roleCode("LE").build()).isActive(true)
                .passwordChangedAt(OffsetDateTime.now()).build();
        when(jwtService.extractEmail("old-token")).thenReturn("lecturer@example.test");
        when(jwtService.isTokenValid("old-token", "lecturer@example.test")).thenReturn(true);
        when(users.findByEmail("lecturer@example.test")).thenReturn(Optional.of(user));
        when(jwtService.extractIssuedAt("old-token")).thenReturn(Instant.now().minusSeconds(10));

        assertThrows(BusinessException.class, () -> service.refreshToken(request));
        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
