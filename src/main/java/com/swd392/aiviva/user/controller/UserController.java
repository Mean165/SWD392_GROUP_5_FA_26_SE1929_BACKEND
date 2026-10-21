package com.swd392.aiviva.user.controller;

import com.swd392.aiviva.common.response.ApiResponse;
import com.swd392.aiviva.common.response.PageResponse;
import com.swd392.aiviva.user.dto.request.CreateUserRequest;
import com.swd392.aiviva.user.dto.request.ResetUserPasswordRequest;
import com.swd392.aiviva.user.dto.request.UpdateUserRequest;
import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('AD')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", userService.getAllUsers()));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchManageableUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        UserFilterRequest filters = new UserFilterRequest();
        filters.setKeyword(keyword);
        filters.setRoleCode(roleCode);
        filters.setIsActive(isActive);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully",
                userService.searchManageableUsers(filters, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", userService.getUserById(id)));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<List<UserResponse>>> filterUsersPost(@RequestBody(required = false) UserFilterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Users filtered successfully", userService.filterUsers(request)));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<UserResponse>>> filterUsersGet(UserFilterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Users filtered successfully", userService.filterUsers(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User created successfully", userService.createUser(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable("id") String id,
                                                                 @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", userService.updateUser(id, request)));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<UserResponse>> lockUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("User account locked", userService.lockUser(id)));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<UserResponse>> unlockUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("User account unlocked", userService.unlockUser(id)));
    }

    @PostMapping("/{id}/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id,
            @Valid @RequestBody ResetUserPasswordRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("User password reset successfully", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
