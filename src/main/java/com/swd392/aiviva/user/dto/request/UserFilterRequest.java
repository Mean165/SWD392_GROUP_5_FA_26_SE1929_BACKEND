package com.swd392.aiviva.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterRequest {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String studentOrStaffCode;
    private String roleCode;
    private String department;
    private Boolean isActive;
}
