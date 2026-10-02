package com.swd392.aiviva.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @Schema(description = "Họ và tên người dùng", example = "Nguyen Van A")
    @NotBlank(message = "Full name is required")
    private String fullName;

    @Schema(description = "Email người dùng", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Schema(description = "Mã số vai trò (AD: Admin, LE: Lecturer, ST: Student)", example = "ST")
    private String roleCode;
}
