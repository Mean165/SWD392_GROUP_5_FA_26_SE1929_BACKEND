package com.swd392.aiviva.user.dto.response;

import java.util.UUID;

public record LecturerSummaryResponse(
        UUID userId,
        String fullName,
        String email,
        String studentOrStaffCode,
        Boolean isActive) {
}
