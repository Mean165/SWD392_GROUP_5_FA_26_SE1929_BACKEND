package com.swd392.aiviva.user.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private String studentOrStaffCode;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private String roleName;
    private RoleResponse role;
}
