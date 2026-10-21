package com.swd392.aiviva.system.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddSubjectTopicsRequest(
        @NotEmpty(message = "At least one topic is required")
        @Size(max = 100, message = "A subject cannot receive more than 100 topics at once")
        List<@NotNull(message = "Topics cannot contain null") @Valid SubjectTopicInput> topics) {
}
