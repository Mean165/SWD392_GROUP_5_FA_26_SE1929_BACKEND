package com.swd392.aiviva.user.mapper;

import com.swd392.aiviva.user.dto.response.RoleResponse;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.entity.User;

public class UserMapper {

    public static RoleResponse toRoleResponse(Role role) {
        if (role == null) {
            return null;
        }
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .description(role.getDescription())
                .build();
    }

    public static UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .studentOrStaffCode(user.getStudentOrStaffCode())
                .department(user.getDepartment())
                .isActive(user.getIsActive())
                .role(toRoleResponse(user.getRole()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
