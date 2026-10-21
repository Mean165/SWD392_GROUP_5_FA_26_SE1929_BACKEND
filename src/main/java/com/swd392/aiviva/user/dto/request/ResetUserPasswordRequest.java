package com.swd392.aiviva.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetUserPasswordRequest {
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "Password must contain between 8 and 128 characters")
    private String newPassword;
}
