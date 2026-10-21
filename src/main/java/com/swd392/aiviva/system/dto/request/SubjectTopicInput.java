package com.swd392.aiviva.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectTopicInput(
        @NotBlank(message = "Topic code is required")
        @Size(max = 255, message = "Topic code must not exceed 255 characters")
        String topicCode,
        @NotBlank(message = "Topic name is required")
        @Size(max = 255, message = "Topic name must not exceed 255 characters")
        String topicName,
        @Size(max = 2000, message = "Topic description must not exceed 2000 characters")
        String description) {
}
