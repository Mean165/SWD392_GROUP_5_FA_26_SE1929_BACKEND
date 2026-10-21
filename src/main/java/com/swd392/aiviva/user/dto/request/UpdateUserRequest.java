package com.swd392.aiviva.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Schema(description = "Họ và tên người dùng", example = "Nguyen Van A")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Schema(description = "Địa chỉ email", example = "user@example.com")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Schema(description = "Mã số vai trò (AD: Admin, LE: Lecturer, ST: Student)", example = "ST")
    private String roleCode;

    @Schema(description = "Trạng thái kích hoạt tài khoản", example = "true")
    private Boolean isActive;
}
