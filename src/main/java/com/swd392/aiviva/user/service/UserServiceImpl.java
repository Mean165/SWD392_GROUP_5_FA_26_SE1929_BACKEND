package com.swd392.aiviva.user.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final UUID DEFAULT_STUDENT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
            RoleRepository roleRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }

        String currentEmail = authentication.getName();
        AppUser currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new BusinessException("Current authenticated user not found"));

        String currentUserRoleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
        boolean isAdminOrLecturer = "AD".equalsIgnoreCase(currentUserRoleCode) || "LE".equalsIgnoreCase(currentUserRoleCode);

        if (!isAdminOrLecturer) {
            throw new BusinessException("Access denied: Only users with AD or LE role can view all users");
        }

        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> filterUsers(UserFilterRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }

        String currentEmail = authentication.getName();
        AppUser currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new BusinessException("Current authenticated user not found"));

        String currentUserRoleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
        boolean isAdminOrLecturer = "AD".equalsIgnoreCase(currentUserRoleCode) || "LE".equalsIgnoreCase(currentUserRoleCode);

        if (!isAdminOrLecturer) {
            throw new BusinessException("Access denied: Only users with AD or LE role can filter user accounts");
        }

        Specification<AppUser> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request != null) {
                if (request.getUserId() != null) {
                    predicates.add(cb.equal(root.get("userId"), request.getUserId()));
                }

                if (request.getFullName() != null && !request.getFullName().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("fullName")), "%" + request.getFullName().trim().toLowerCase() + "%"));
                }

                if (request.getEmail() != null && !request.getEmail().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("email")), "%" + request.getEmail().trim().toLowerCase() + "%"));
                }

                if (request.getStudentOrStaffCode() != null && !request.getStudentOrStaffCode().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("studentOrStaffCode")), "%" + request.getStudentOrStaffCode().trim().toLowerCase() + "%"));
                }

                if (request.getIsActive() != null) {
                    predicates.add(cb.equal(root.get("isActive"), request.getIsActive()));
                }

                if (request.getRoleId() != null) {
                    Join<AppUser, Role> roleJoin = root.join("role", JoinType.LEFT);
                    predicates.add(cb.equal(roleJoin.get("roleId"), request.getRoleId()));
                } else if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
                    Join<AppUser, Role> roleJoin = root.join("role", JoinType.LEFT);
                    predicates.add(cb.equal(cb.upper(roleJoin.get("roleCode")), request.getRoleCode().trim().toUpperCase()));
                }

                if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                    String kw = "%" + request.getKeyword().trim().toLowerCase() + "%";
                    Predicate nameLike = cb.like(cb.lower(root.get("fullName")), kw);
                    Predicate emailLike = cb.like(cb.lower(root.get("email")), kw);
                    Predicate codeLike = cb.like(cb.lower(root.get("studentOrStaffCode")), kw);
                    predicates.add(cb.or(nameLike, emailLike, codeLike));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(String id) {
        AppUser user = findUserByIdentifier(id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BusinessException("Email already in use: " + request.getEmail());
        }

        String roleCode = (request.getRoleCode() != null && !request.getRoleCode().isBlank()) 
                ? request.getRoleCode().trim().toUpperCase() 
                : "ST";
        Role role = roleRepository.findByRoleCode(roleCode)
                .orElseGet(() -> roleRepository.findById(DEFAULT_STUDENT_ROLE_ID)
                        .orElseThrow(() -> new BusinessException("Role not found with code: " + roleCode)));

        UUID userId = UUID.randomUUID();
        String studentOrStaffCode = generateNextStudentOrStaffCode(role.getRoleCode());

        AppUser user = AppUser.builder()
                .userId(userId)
                .fullName(request.getFullName())
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode("Default@123"))
                .studentOrStaffCode(studentOrStaffCode)
                .role(role)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        AppUser savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    private String generateNextStudentOrStaffCode(String roleCode) {
        String prefix = (roleCode != null && !roleCode.isBlank()) ? roleCode.toUpperCase() : "ST";
        long sequence = userRepository.count() + 1;
        String candidateCode = prefix + sequence;

        while (userRepository.existsByStudentOrStaffCode(candidateCode)) {
            sequence++;
            candidateCode = prefix + sequence;
        }
        return candidateCode;
    }

    @Override
    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("User is not authenticated");
        }

        String currentEmail = authentication.getName();
        AppUser currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new BusinessException("Current authenticated user not found"));

        AppUser targetUser = "me".equalsIgnoreCase(id.trim()) ? currentUser : findUserByIdentifier(id.trim());

        boolean isSelfUpdate = currentUser.getUserId().equals(targetUser.getUserId());

        if (!isSelfUpdate) {
            String currentUserRoleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
            String targetUserRoleCode = targetUser.getRole() != null ? targetUser.getRole().getRoleCode() : "";

            if (currentUserRoleCode.equalsIgnoreCase(targetUserRoleCode)) {
                throw new BusinessException("Access denied: Users with the same role code (" + currentUserRoleCode + ") cannot update each other");
            }

            boolean isAdminOrLecturer = "AD".equalsIgnoreCase(currentUserRoleCode) || "LE".equalsIgnoreCase(currentUserRoleCode);
            if (!isAdminOrLecturer) {
                throw new BusinessException("Access denied: You do not have permission to update another user account");
            }

            if ("LE".equalsIgnoreCase(currentUserRoleCode) && "AD".equalsIgnoreCase(targetUserRoleCode)) {
                throw new BusinessException("Access denied: Lecturer (LE) cannot update Admin (AD) account");
            }
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            targetUser.setFullName(request.getFullName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (!request.getEmail().equalsIgnoreCase(targetUser.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already in use: " + request.getEmail());
            }
            targetUser.setEmail(request.getEmail());
        }
        if (request.getIsActive() != null) {
            targetUser.setIsActive(request.getIsActive());
        }

        if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
            String currentUserRoleCode = currentUser.getRole() != null ? currentUser.getRole().getRoleCode() : "";
            if (!"AD".equalsIgnoreCase(currentUserRoleCode)) {
                throw new BusinessException("Access denied: Only users with AD role code can modify user roles");
            }

            String finalRoleCode = request.getRoleCode().trim().toUpperCase();
            String existingRoleCode = targetUser.getRole() != null ? targetUser.getRole().getRoleCode() : "";

            if (!finalRoleCode.equalsIgnoreCase(existingRoleCode)) {
                Role newRole = roleRepository.findByRoleCode(finalRoleCode)
                        .orElseThrow(() -> new BusinessException("Role not found with code: " + finalRoleCode));
                targetUser.setRole(newRole);

                String newStudentOrStaffCode = generateNextStudentOrStaffCode(finalRoleCode);
                targetUser.setStudentOrStaffCode(newStudentOrStaffCode);
            }
        }

        AppUser updatedUser = userRepository.save(targetUser);
        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    private AppUser findUserByIdentifier(String identifier) {
        try {
            UUID uuid = UUID.fromString(identifier);
            return userRepository.findById(uuid)
                    .orElseGet(() -> userRepository.findByStudentOrStaffCode(identifier)
                            .orElseGet(() -> userRepository.findByEmail(identifier)
                                    .orElseThrow(() -> new BusinessException("User not found: " + identifier))));
        } catch (IllegalArgumentException e) {
            return userRepository.findByStudentOrStaffCode(identifier)
                    .orElseGet(() -> userRepository.findByEmail(identifier)
                            .orElseThrow(() -> new BusinessException("User not found: " + identifier)));
        }
    }
}
