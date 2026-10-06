package com.swd392.aiviva.auth.dto.response;

import com.swd392.aiviva.user.dto.response.UserResponse;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UUID userId;
    private String email;
    private String fullName;
    private String studentOrStaffCode;
    private String roleName;
    private UserResponse user;
}
