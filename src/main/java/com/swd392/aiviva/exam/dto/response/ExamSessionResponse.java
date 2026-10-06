package com.swd392.aiviva.exam.dto.response;

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
public class ExamSessionResponse {

    private UUID sessionId;
    private UUID createdById;
    private String createdByName;
    private String sessionName;
    private Integer maxMainQuestions;
    private Integer maxFollowupPerQuestion;
    private Integer timeLimitMinutes;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime createdAt;
}
