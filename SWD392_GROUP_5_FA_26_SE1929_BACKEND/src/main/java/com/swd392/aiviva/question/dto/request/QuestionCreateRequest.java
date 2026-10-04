package com.swd392.aiviva.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class QuestionCreateRequest {

    @NotNull(message = "Topic ID is required")
    private UUID topicId;

    @NotNull(message = "Created By is required")
    private UUID createdBy;

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotBlank(message = "Bloom level is required")
    private String bloomLevel;

    @NotBlank(message = "Source type is required")
    private String sourceType;

    @NotBlank(message = "Approval status is required")
    private String approvalStatus;
}
