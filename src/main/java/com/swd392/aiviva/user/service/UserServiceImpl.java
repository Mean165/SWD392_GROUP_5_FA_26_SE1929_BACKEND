package com.swd392.aiviva.user.service;

import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.mapper.UserMapper;
import com.swd392.aiviva.user.repository.RoleRepository;
import com.swd392.aiviva.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final UUID DEFAULT_STUDENT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Set<String> MANAGED_ROLE_CODES = Set.of("LE", "ST");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailService accountEmailService;

    public UserServiceImpl(UserRepository userRepository,
            RoleRepository roleRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AccountEmailService accountEmailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.accountEmailService = accountEmailService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        requireAdminActor();
        return userRepository.findAll(manageableUsers(null)).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> filterUsers(UserFilterRequest request) {
        requireAdminActor();
        return userRepository.findAll(manageableUsers(request)).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchManageableUsers(UserFilterRequest request, int page, int size) {
        requireAdminActor();
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("Page must be non-negative and size must be between 1 and 100");
        }
        Page<UserResponse> result = userRepository.findAll(manageableUsers(request),
                        PageRequest.of(page, size, Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("userId"))))
                .map(userMapper::toResponse);
        return PageResponse.<UserResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LecturerSummaryResponse> listLecturers() {
        requireAdminActor();
        return userRepository.findAll().stream()
                .filter(user -> "LE".equalsIgnoreCase(roleCode(user)))
                .sorted(java.util.Comparator.comparing(AppUser::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toLecturerSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LecturerSummaryResponse getLecturer(UUID lecturerId) {
        requireAdminActor();
        AppUser user = userRepository.findById(lecturerId)
                .filter(item -> "LE".equalsIgnoreCase(roleCode(item)))
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer not found"));
        return toLecturerSummary(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(String id) {
        requireAdminActor();
        AppUser user = findUserByIdentifier(id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        requireAdminActor();
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email is already in use");
        }

        String roleCode = request.getRoleCode() == null || request.getRoleCode().isBlank()
                ? "ST" : request.getRoleCode().trim().toUpperCase(java.util.Locale.ROOT);
        requireManagedRole(roleCode);
        Role role = roleRepository.findByRoleCode(roleCode)
                .orElseGet(() -> "ST".equals(roleCode)
                        ? roleRepository.findById(DEFAULT_STUDENT_ROLE_ID).orElseThrow(() -> new BusinessException("Role not found with code: ST"))
                        : null);
        if (role == null) {
            throw new BusinessException("Role not found with code: " + roleCode);
        }

        UUID userId = UUID.randomUUID();
        String initialPassword = UUID.randomUUID().toString();
        String studentOrStaffCode = generateNextStudentOrStaffCode(role.getRoleCode());
        OffsetDateTime createdAt = OffsetDateTime.now();

        AppUser user = AppUser.builder()
                .userId(userId)
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .mustChangePassword(true)
                .studentOrStaffCode(studentOrStaffCode)
                .role(role)
                .isActive(true)
                .createdAt(createdAt)
                .passwordChangedAt(createdAt.minusNanos(1_000_000))
                .build();

        AppUser savedUser = userRepository.save(user);
        accountEmailService.sendInitialCredentials(savedUser, initialPassword);
        return userMapper.toResponse(savedUser);
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

    @Override
    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest request) {
        AppUser actor = requireAdminActor();
        AppUser targetUser = findUserByIdentifier(id.trim());
        requireManageableAccount(targetUser);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            targetUser.setFullName(request.getFullName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
            if (!email.equalsIgnoreCase(targetUser.getEmail())
                    && userRepository.existsByEmailIgnoreCaseAndUserIdNot(email, targetUser.getUserId())) {
                throw new BusinessException("Email is already in use");
            }
            targetUser.setEmail(email);
        }
        if (request.getIsActive() != null) {
            if (!request.getIsActive() && actor.getUserId().equals(targetUser.getUserId())) {
                throw new BusinessException("You cannot deactivate your own account");
            }
            targetUser.setIsActive(request.getIsActive());
        }

        if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
            String finalRoleCode = request.getRoleCode().trim().toUpperCase(java.util.Locale.ROOT);
            requireManagedRole(finalRoleCode);
            String existingRoleCode = roleCode(targetUser);

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
        AppUser actor = requireAdminActor();
        AppUser target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireManageableAccount(target);
        if (actor.getUserId().equals(target.getUserId())) {
            throw new BusinessException("You cannot delete your own account");
        }
        if ("LE".equalsIgnoreCase(roleCode(target))) {
            throw new BusinessException("Lecturer accounts cannot be hard-deleted; lock the account to revoke access");
        }
        userRepository.delete(target);
    }

    @Override
    @Transactional
    public UserResponse lockUser(UUID id) {
        AppUser actor = requireAdminActor();
        AppUser target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireManageableAccount(target);
        if (actor.getUserId().equals(target.getUserId())) {
            throw new BusinessException("You cannot lock your own account");
        }
        target.setIsActive(false);
        return userMapper.toResponse(userRepository.save(target));
    }

    @Override
    @Transactional
    public UserResponse unlockUser(UUID id) {
        requireAdminActor();
        AppUser target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireManageableAccount(target);
        target.setIsActive(true);
        return userMapper.toResponse(userRepository.save(target));
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        requireAdminActor();
        AppUser target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireManageableAccount(target);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw new BusinessException("Password must contain between 8 and 128 characters");
        }
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setPasswordChangedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        userRepository.save(target);
    }

    private Specification<AppUser> manageableUsers(UserFilterRequest request) {
        if (request != null && request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
            requireManagedRole(request.getRoleCode().trim().toUpperCase(java.util.Locale.ROOT));
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<AppUser, Role> role = root.join("role", JoinType.INNER);
            predicates.add(cb.upper(role.get("roleCode")).in(MANAGED_ROLE_CODES));
            if (request != null) {
                if (request.getUserId() != null) predicates.add(cb.equal(root.get("userId"), request.getUserId()));
                addContainsPredicate(request.getFullName(), root.get("fullName"), cb, predicates);
                addContainsPredicate(request.getEmail(), root.get("email"), cb, predicates);
                addContainsPredicate(request.getStudentOrStaffCode(), root.get("studentOrStaffCode"), cb, predicates);
                if (request.getIsActive() != null) predicates.add(cb.equal(root.get("isActive"), request.getIsActive()));
                if (request.getRoleId() != null) predicates.add(cb.equal(role.get("roleId"), request.getRoleId()));
                if (request.getRoleCode() != null && !request.getRoleCode().isBlank()) {
                    predicates.add(cb.equal(cb.upper(role.get("roleCode")), request.getRoleCode().trim().toUpperCase(java.util.Locale.ROOT)));
                }
                if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                    String keyword = contains(request.getKeyword());
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("fullName")), keyword),
                            cb.like(cb.lower(root.get("email")), keyword),
                            cb.like(cb.lower(root.get("studentOrStaffCode")), keyword)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addContainsPredicate(String value, jakarta.persistence.criteria.Path<String> field,
            jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> predicates) {
        if (value != null && !value.isBlank()) predicates.add(cb.like(cb.lower(field), contains(value)));
    }

    private String contains(String value) {
        return "%" + value.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }

    private AppUser requireAdminActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AD".equals(authority.getAuthority()));
        if (!isAdmin) throw new AccessDeniedException("Only Admin can manage accounts");
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated Admin account not found"));
    }

    private String roleCode(AppUser user) {
        return user.getRole() == null || user.getRole().getRoleCode() == null
                ? "" : user.getRole().getRoleCode();
    }

    private void requireManagedRole(String roleCode) {
        if (!MANAGED_ROLE_CODES.contains(roleCode)) {
            throw new BusinessException("Only Lecturer (LE) and Student (ST) accounts can be managed here");
        }
    }

    private void requireManageableAccount(AppUser user) {
        requireManagedRole(roleCode(user).toUpperCase(java.util.Locale.ROOT));
    }

    private LecturerSummaryResponse toLecturerSummary(AppUser user) {
        return new LecturerSummaryResponse(user.getUserId(), user.getFullName(), user.getEmail(),
                user.getStudentOrStaffCode(), user.getIsActive());
    }

    private AppUser findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("User not found");
        }
        try {
            UUID uuid = UUID.fromString(identifier);
            return userRepository.findById(uuid)
                    .orElseGet(() -> userRepository.findByStudentOrStaffCode(identifier)
                            .orElseGet(() -> userRepository.findByEmail(identifier.trim().toLowerCase(java.util.Locale.ROOT))
                                    .orElseThrow(() -> new ResourceNotFoundException("User not found"))));
        } catch (IllegalArgumentException e) {
            return userRepository.findByStudentOrStaffCode(identifier)
                    .orElseGet(() -> userRepository.findByEmail(identifier.trim().toLowerCase(java.util.Locale.ROOT))
                            .orElseThrow(() -> new ResourceNotFoundException("User not found")));
        }
    }
}
