package com.swd392.aiviva.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.service.AccountEmailService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    private final UUID adminId = UUID.randomUUID();
    private final String adminEmail = "admin@example.test";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountEmailService accountEmailService;

    private UserServiceImpl service;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = AppUser.builder().userId(adminId).email(adminEmail)
                .role(Role.builder().roleCode("AD").build()).isActive(true).build();
        service = new UserServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder, accountEmailService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                adminEmail, "n/a", List.of(new SimpleGrantedAuthority("ROLE_AD"))));
        lenient().when(userRepository.findByEmail(adminEmail)).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountManagementRejectsCreatingAnAdmin() {
        CreateUserRequest request = CreateUserRequest.builder()
                .fullName("New Admin").email("new-admin@example.test").roleCode("AD").build();

        assertThrows(BusinessException.class, () -> service.createUser(request));
        verify(roleRepository, never()).findByRoleCode(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createUsesTemporaryPasswordRequiresChangeAndEmailsCredentials() {
        Role lecturerRole = Role.builder().roleCode("LE").roleName("Lecturer").build();
        UserResponse response = UserResponse.builder().userId(UUID.randomUUID()).email("new@example.test").build();
        when(userRepository.existsByEmailIgnoreCase("new@example.test")).thenReturn(false);
        when(roleRepository.findByRoleCode("LE")).thenReturn(Optional.of(lecturerRole));
        when(userRepository.existsByStudentOrStaffCode("LE1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(AppUser.class))).thenReturn(response);
        CreateUserRequest request = CreateUserRequest.builder()
                .fullName(" Lecturer ").email(" NEW@EXAMPLE.TEST ")
                .roleCode("LE").build();

        assertEquals(response, service.createUser(request));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(saved.capture());
        assertEquals("new@example.test", saved.getValue().getEmail());
        assertEquals("bcrypt-hash", saved.getValue().getPasswordHash());
        assertEquals(true, saved.getValue().getMustChangePassword());
        assertNotNull(saved.getValue().getPasswordChangedAt());
        ArgumentCaptor<String> initialPassword = ArgumentCaptor.forClass(String.class);
        verify(accountEmailService).sendInitialCredentials(org.mockito.ArgumentMatchers.eq(saved.getValue()), initialPassword.capture());
        assertEquals(36, initialPassword.getValue().length());
        org.junit.jupiter.api.Assertions.assertNotEquals("123456", initialPassword.getValue());
        assertFalse(java.util.Arrays.stream(UserResponse.class.getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("password")));
    }

    @Test
    void createSendsTemporaryPasswordOnlyAfterSavingTheAccount() {
        Role studentRole = Role.builder().roleCode("ST").roleName("Student").build();
        when(userRepository.existsByEmailIgnoreCase("new@example.test")).thenReturn(false);
        when(roleRepository.findByRoleCode("ST")).thenReturn(Optional.of(studentRole));
        when(userRepository.existsByStudentOrStaffCode("ST1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(AppUser.class))).thenReturn(UserResponse.builder().email("new@example.test").build());

        service.createUser(CreateUserRequest.builder().fullName("Student").email("new@example.test").build());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, accountEmailService);
        order.verify(userRepository).save(any(AppUser.class));
        order.verify(accountEmailService).sendInitialCredentials(any(AppUser.class), anyString());
    }

    @Test
    void nonAdminCannotManageAccounts() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "lecturer@example.test", "n/a", List.of(new SimpleGrantedAuthority("ROLE_LE"))));

        assertThrows(AccessDeniedException.class, service::getAllUsers);
    }

    @Test
    void lockDeactivatesLecturerAndResetStoresOnlyEncodedPassword() {
        UUID lecturerId = UUID.randomUUID();
        AppUser lecturer = AppUser.builder().userId(lecturerId).email("lecturer@example.test")
                .role(Role.builder().roleCode("LE").build()).isActive(true).build();
        when(userRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("ChangedPassword-123")).thenReturn("new-bcrypt-hash");
        when(userMapper.toResponse(lecturer)).thenReturn(UserResponse.builder().userId(lecturerId).isActive(false).build());

        service.lockUser(lecturerId);
        assertFalse(lecturer.getIsActive());

        service.resetPassword(lecturerId, "ChangedPassword-123");
        assertEquals("new-bcrypt-hash", lecturer.getPasswordHash());
        assertNotNull(lecturer.getPasswordChangedAt());
    }
}
