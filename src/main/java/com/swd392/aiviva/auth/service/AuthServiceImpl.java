package com.swd392.aiviva.auth.service;

import com.swd392.aiviva.auth.dto.request.LoginRequest;
import com.swd392.aiviva.auth.dto.request.RefreshTokenRequest;
import com.swd392.aiviva.auth.dto.request.RegisterRequest;
import com.swd392.aiviva.auth.dto.response.LoginResponse;
import com.swd392.aiviva.auth.entity.UserAuthentication;
import com.swd392.aiviva.auth.repository.UserAuthenticationRepository;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.entity.User;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAuthenticationRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserAuthenticationRepository userAuthRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userAuthRepository = userAuthRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email is already registered");
        }

        Role studentRole = roleRepository.findByCode("ST")
                .orElseGet(() -> roleRepository.save(Role.builder().code("ST").description("Student").build()));

        String studentCode = generateNextCode("ST");

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .studentOrStaffCode(studentCode)
                .department(request.getDepartment())
                .role(studentRole)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        UserAuthentication userAuth = UserAuthentication.builder()
                .username(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .provider("LOCAL")
                .user(savedUser)
                .build();

        userAuthRepository.save(userAuth);

        return UserMapper.toUserResponse(savedUser);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        UserAuthentication userAuth = userAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), userAuth.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessException("Account is disabled");
        }

        String token = jwtService.generateToken(user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserMapper.toUserResponse(user))
                .build();
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String username = jwtService.extractUsername(request.getRefreshToken());
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        String newToken = jwtService.generateToken(user.getEmail());

        return LoginResponse.builder()
                .token(newToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(UserMapper.toUserResponse(user))
                .build();
    }

    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    @Override
    public UserResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User profile not found"));
        return UserMapper.toUserResponse(user);
    }

    private String generateNextCode(String prefix) {
        List<String> existingCodes = userRepository.findAllCodesByPrefix(prefix);
        int maxIndex = 0;
        for (String code : existingCodes) {
            if (code != null && code.startsWith(prefix)) {
                String numericPart = code.substring(prefix.length());
                try {
                    int val = Integer.parseInt(numericPart);
                    if (val > maxIndex) {
                        maxIndex = val;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + (maxIndex + 1);
    }
}
