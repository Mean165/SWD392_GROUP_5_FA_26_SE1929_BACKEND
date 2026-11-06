package com.swd392.aiviva.interview.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionTurnResponse {

    private UUID turnId;
    private UUID attemptId;
    private UUID mainQuestionId;
    private Integer turnOrder;
    private String turnType;
    private String aiQuestionText;
    private String transcriptText;
    private String audioSegmentUrl;
    private String aiReasoning;
    private OffsetDateTime createdAt;
}
