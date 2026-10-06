package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamSessionRequest {

    @NotBlank(message = "Session name is required")
    private String sessionName;

    @NotNull(message = "Max main questions is required")
    @Min(value = 1, message = "Max main questions must be at least 1")
    private Integer maxMainQuestions;

    @NotNull(message = "Max follow-up per question is required")
    @Min(value = 0, message = "Max follow-up per question must be greater than or equal to 0")
    private Integer maxFollowupPerQuestion;

    @NotNull(message = "Time limit minutes is required")
    @Min(value = 1, message = "Time limit minutes must be at least 1")
    private Integer timeLimitMinutes;

    private String status;

    private OffsetDateTime startTime;

    private UUID createdBy;
}
