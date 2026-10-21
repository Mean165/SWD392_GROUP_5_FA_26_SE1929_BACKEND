package com.swd392.aiviva.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSubjectRequest(
        @NotBlank(message = "Subject code is required")
        @Size(max = 100, message = "Subject code must not exceed 100 characters")
        String subjectCode,
        @NotBlank(message = "Subject name is required")
        @Size(max = 255, message = "Subject name must not exceed 255 characters")
        String subjectName,
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description) {
}
