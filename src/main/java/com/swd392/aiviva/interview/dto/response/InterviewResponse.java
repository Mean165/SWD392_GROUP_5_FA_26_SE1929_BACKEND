package com.swd392.aiviva.interview.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewResponse {

    private UUID attemptId;
    private UUID assignmentId;
    private UUID sessionId;
    private UUID studentId;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private InteractionTurnResponse currentTurn;
    private List<InteractionTurnResponse> turns;
}
