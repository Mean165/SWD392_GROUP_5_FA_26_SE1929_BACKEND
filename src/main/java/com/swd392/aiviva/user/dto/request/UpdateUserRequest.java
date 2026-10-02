package com.swd392.aiviva.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String fullName;

    @Schema(description = "Địa chỉ email", example = "user@example.com")
    private String email;

    @Schema(description = "Mã số vai trò (AD: Admin, LE: Lecturer, ST: Student)", example = "ST")
    private String roleCode;

    @Schema(description = "Trạng thái kích hoạt tài khoản", example = "true")
    private Boolean isActive;
}
