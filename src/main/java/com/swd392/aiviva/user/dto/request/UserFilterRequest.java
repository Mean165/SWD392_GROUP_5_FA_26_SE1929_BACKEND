package com.swd392.aiviva.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFilterRequest {

    @Schema(description = "ID người dùng (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;

    @Schema(description = "Họ và tên (tìm kiếm gần đúng)", example = "Nguyen Van")
    private String fullName;

    @Schema(description = "Địa chỉ email (tìm kiếm gần đúng)", example = "admin")
    private String email;

    @Schema(description = "Mã sinh viên/giảng viên (tìm kiếm gần đúng)", example = "ST1")
    private String studentOrStaffCode;

    @Schema(description = "Mã vai trò (AD: Admin, LE: Lecturer, ST: Student)", example = "ST")
    private String roleCode;

    @Schema(description = "ID vai trò (UUID)", example = "00000000-0000-0000-0000-000000000003")
    private UUID roleId;

    @Schema(description = "Trạng thái kích hoạt tài khoản", example = "true")
    private Boolean isActive;

    @Schema(description = "Từ khóa tìm kiếm chung (tìm trên Họ tên, Email, Mã số)", example = "Nguyen")
    private String keyword;
}
