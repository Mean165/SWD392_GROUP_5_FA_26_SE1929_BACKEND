package com.swd392.aiviva.system.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSubjectRequest(
        @NotBlank(message = "Subject code is required")
        @Size(max = 100, message = "Subject code must not exceed 100 characters")
        String subjectCode,
        @NotBlank(message = "Subject name is required")
        @Size(max = 255, message = "Subject name must not exceed 255 characters")
        String subjectName,
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,
        @Size(max = 100, message = "A subject cannot contain more than 100 topics at once")
        List<@NotNull(message = "Topics cannot contain null") @Valid SubjectTopicInput> topics) {
}
