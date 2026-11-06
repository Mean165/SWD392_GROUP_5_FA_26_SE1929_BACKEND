package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamEntryEligibilityResponse {

    private boolean canEnter;
    private String reason;
    private OffsetDateTime opensAt;
    private OffsetDateTime closesAt;
    private String reasonCode;
    private int attemptsUsed;
    private int attemptsAllowed;
    private int attemptsRemaining;
    private java.util.UUID nextAttemptId;
    private Integer nextAttemptNo;
    private OffsetDateTime retryAt;
}
