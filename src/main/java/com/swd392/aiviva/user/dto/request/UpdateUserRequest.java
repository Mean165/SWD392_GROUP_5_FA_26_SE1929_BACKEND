package com.swd392.aiviva.user.dto.request;

import com.swd392.aiviva.user.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String fullName;
    private String email;
    private String phoneNumber;
    private RoleType role;
    private Boolean enabled;
}

