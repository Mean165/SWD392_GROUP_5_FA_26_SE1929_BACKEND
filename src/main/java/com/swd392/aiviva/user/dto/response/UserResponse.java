package com.swd392.aiviva.user.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String studentOrStaffCode;
    private String department;
    private Boolean isActive;
    private RoleResponse role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
