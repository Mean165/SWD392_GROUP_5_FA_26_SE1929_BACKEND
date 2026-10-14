package com.swd392.aiviva.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionRequest {

    @NotNull(message = "topicId is required")
    private UUID topicId;

    private UUID createdBy;

    @NotBlank(message = "questionText is required")
    private String questionText;

    private String bloomLevel;

    private String sourceType;

    private String approvalStatus;
}
