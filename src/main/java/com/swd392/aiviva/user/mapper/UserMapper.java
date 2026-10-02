package com.swd392.aiviva.user.mapper;

import com.swd392.aiviva.user.dto.response.RoleResponse;
import com.swd392.aiviva.user.dto.response.UserResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.entity.Role;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(AppUser user) {
        if (user == null) {
            return null;
        }

        RoleResponse roleResponse = null;
        String roleName = null;

        if (user.getRole() != null) {
            Role r = user.getRole();
            roleName = r.getRoleName() != null ? r.getRoleName() : r.getRoleCode();
            roleResponse = RoleResponse.builder()
                    .roleId(r.getRoleId())
                    .roleCode(r.getRoleCode())
                    .roleName(r.getRoleName())
                    .description(r.getDescription())
                    .createdAt(r.getCreatedAt())
                    .build();
        }

        return UserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .studentOrStaffCode(user.getStudentOrStaffCode())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .roleName(roleName)
                .role(roleResponse)
                .build();
    }
}
