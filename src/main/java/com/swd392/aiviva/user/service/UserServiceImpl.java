package com.swd392.aiviva.user.service;

import com.swd392.aiviva.auth.entity.UserAuthentication;
import com.swd392.aiviva.auth.repository.UserAuthenticationRepository;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.entity.User;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.specification.UserSpecification;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAuthenticationRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserAuthenticationRepository userAuthRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userAuthRepository = userAuthRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserResponse> getAllUsers() {
        verifyHasRole("AD", "LE");
        return userRepository.findAll().stream()
                .map(UserMapper::toUserResponse)
                .toList();
    }

    @Override
    public UserResponse getUserByIdentifier(String identifier) {
        User user = findUserByIdentifier(identifier);
        return UserMapper.toUserResponse(user);
    }

    @Override
    public List<UserResponse> filterUsers(UserFilterRequest filterRequest) {
        verifyHasRole("AD", "LE");
        return userRepository.findAll(UserSpecification.filter(filterRequest)).stream()
                .map(UserMapper::toUserResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already in use");
        }

        String roleCode = StringUtils.hasText(request.getRoleCode())
                ? request.getRoleCode().trim().toUpperCase()
                : "ST";

        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code(roleCode)
                        .description(roleCode.equalsIgnoreCase("AD") ? "Administrator"
                                : roleCode.equalsIgnoreCase("LE") ? "Lecturer" : "Student")
                        .build()));

        String code = generateNextCode(roleCode);

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .studentOrStaffCode(code)
                .department(request.getDepartment())
                .role(role)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();

        User savedUser = userRepository.save(user);

        UserAuthentication auth = UserAuthentication.builder()
                .username(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .provider("LOCAL")
                .user(savedUser)
                .build();

        userAuthRepository.save(auth);

        return UserMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUser(String identifier, UpdateUserRequest request) {
        User user = findUserByIdentifier(identifier);

        if (StringUtils.hasText(request.getRoleCode())) {
            String newRoleCode = request.getRoleCode().trim().toUpperCase();
            if (user.getRole() == null || !newRoleCode.equalsIgnoreCase(user.getRole().getCode())) {
                // Changing roleCode requires AD role
                if (!hasRole("AD")) {
                    throw new BusinessException("Only AD role can update user roleCode");
                }

                Role newRole = roleRepository.findByCode(newRoleCode)
                        .orElseGet(() -> roleRepository.save(Role.builder()
                                .code(newRoleCode)
                                .description(newRoleCode.equalsIgnoreCase("AD") ? "Administrator"
                                        : newRoleCode.equalsIgnoreCase("LE") ? "Lecturer" : "Student")
                                .build()));

                user.setRole(newRole);
                String newCode = generateNextCode(newRoleCode);
                user.setStudentOrStaffCode(newCode);
            }
        }

        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName());
        }

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        User updatedUser = userRepository.save(user);
        return UserMapper.toUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        userAuthRepository.findByUser(user).ifPresent(userAuthRepository::delete);
        userRepository.delete(user);
    }

    private User findUserByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return userRepository.findById(id)
                    .orElseGet(() -> userRepository.findByStudentOrStaffCode(identifier)
                            .orElseGet(() -> userRepository.findByEmail(identifier)
                                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier))));
        } catch (NumberFormatException e) {
            return userRepository.findByStudentOrStaffCode(identifier)
                    .orElseGet(() -> userRepository.findByEmail(identifier)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier)));
        }
    }

    private boolean hasRole(String... roleCodes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(u -> u.getRole() != null && Arrays.asList(roleCodes).contains(u.getRole().getCode()))
                .orElse(false);
    }

    private void verifyHasRole(String... roleCodes) {
        if (!hasRole(roleCodes)) {
            throw new BusinessException("Access denied. Requires " + String.join(" or ", roleCodes) + " role.");
        }
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
