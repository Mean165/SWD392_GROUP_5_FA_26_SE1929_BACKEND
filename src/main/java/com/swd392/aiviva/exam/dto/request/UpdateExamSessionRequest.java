package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;
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

    private UUID topicId;

    @Min(value = 1, message = "Max main questions must be at least 1")
    private Integer maxMainQuestions;

    @Min(value = 0, message = "Max follow-up per question must be greater than or equal to 0")
    private Integer maxFollowupPerQuestion;

    @Min(value = 1, message = "Time limit minutes must be at least 1")
    private Integer timeLimitMinutes;

    private String status;

    private OffsetDateTime startTime;

    @Min(value = 0, message = "Buffer minutes must be non-negative")
    private Integer bufferMinutes;

    @Min(value = 0, message = "Avoid repeat window must be non-negative")
    private Integer avoidRepeatWindow;

    @Min(value = 0, message = "Maximum overlap percent must be non-negative")
    @Max(value = 100, message = "Maximum overlap percent must be at most 100")
    private Integer maxOverlapPercent;

    private String description;
    private Map<String, Integer> bloomDistribution;
}
