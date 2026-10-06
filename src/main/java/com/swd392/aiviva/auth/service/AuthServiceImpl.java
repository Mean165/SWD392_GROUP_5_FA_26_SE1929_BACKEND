package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.request.RegisterRequest;
import com.swd392.aiviva.auth.dto.response.LoginResponse;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final UUID DEFAULT_STUDENT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already registered: " + email);
        }

        Role role = roleRepository.findByRoleCode("ST")
                .orElseGet(() -> roleRepository.findById(DEFAULT_STUDENT_ROLE_ID)
                        .orElseGet(() -> roleRepository.save(
                                Role.builder()
                                        .roleId(DEFAULT_STUDENT_ROLE_ID)
                                        .roleCode("ST")
                                        .roleName("Student")
                                        .description("Student role")
                                        .createdAt(OffsetDateTime.now())
                                        .build()
                        )));

        String studentCode = request.getStudentOrStaffCode();
        if (studentCode != null && !studentCode.trim().isBlank()) {
            studentCode = studentCode.trim();
            if (userRepository.existsByStudentOrStaffCode(studentCode)) {
                throw new BusinessException("Student or staff code already exists: " + studentCode);
            }
        } else {
            studentCode = generateNextStudentOrStaffCode(role.getRoleCode());
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        AppUser appUser = AppUser.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(encodedPassword)
                .studentOrStaffCode(studentCode)
                .role(role)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        AppUser savedUser = userRepository.save(appUser);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String inputIdentifier = request.getEmail().trim();

        AppUser user = userRepository.findByEmailOrStudentOrStaffCode(inputIdentifier, inputIdentifier)
                .orElseThrow(() -> new BusinessException("Invalid email/code or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email/code or password");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessException("User account is inactive");
        }

        String roleName = user.getRole() != null ? user.getRole().getRoleCode() : "USER";
        Map<String, Object> claims = Map.of(
                "userId", user.getUserId().toString(),
                "role", roleName,
                "fullName", user.getFullName()
        );

        String token = jwtService.generateToken(user.getEmail(), claims);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .studentOrStaffCode(user.getStudentOrStaffCode())
                .roleName(roleName)
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String email = jwtService.extractEmail(request.getRefreshToken());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        String roleName = user.getRole() != null ? user.getRole().getRoleCode() : "USER";
        Map<String, Object> claims = Map.of(
                "userId", user.getUserId().toString(),
                "role", roleName,
                "fullName", user.getFullName()
        );

        String token = jwtService.generateToken(user.getEmail(), claims);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .studentOrStaffCode(user.getStudentOrStaffCode())
                .roleName(roleName)
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }

        String email = authentication.getName();
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User profile not found for email: " + email));

        return userMapper.toResponse(user);
    }

    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    private String generateNextStudentOrStaffCode(String roleCode) {
        String prefix = (roleCode != null && !roleCode.isBlank()) ? roleCode.toUpperCase() : "ST";
        long sequence = 1;
        String candidateCode = prefix + sequence;

        while (userRepository.existsByStudentOrStaffCode(candidateCode)) {
            sequence++;
            candidateCode = prefix + sequence;
        }
        return candidateCode;
    }
}
