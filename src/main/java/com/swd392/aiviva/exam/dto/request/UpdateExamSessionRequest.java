package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExamSessionRequest {

    private String sessionName;

    @Min(value = 1, message = "Max main questions must be at least 1")
    private Integer maxMainQuestions;

    @Min(value = 0, message = "Max follow-up per question must be greater than or equal to 0")
    private Integer maxFollowupPerQuestion;

    @Min(value = 1, message = "Time limit minutes must be at least 1")
    private Integer timeLimitMinutes;

    private String status;

    private OffsetDateTime startTime;
}
