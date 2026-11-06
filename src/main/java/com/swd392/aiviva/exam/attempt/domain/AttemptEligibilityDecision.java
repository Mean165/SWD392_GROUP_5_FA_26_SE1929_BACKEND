package com.swd392.aiviva.exam.attempt.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttemptEligibilityDecision(boolean allowed, String code, OffsetDateTime retryAt,
        UUID examAttemptId, int attemptNo, int attemptsUsed, int attemptsAllowed, int attemptsRemaining) {
    public static AttemptEligibilityDecision allowed(UUID attemptId, int attemptNo, int used, int allowed) {
        return new AttemptEligibilityDecision(true, null, null, attemptId, attemptNo, used, allowed, Math.max(0, allowed - used));
    }

    public static AttemptEligibilityDecision rejected(String code, OffsetDateTime retryAt,
        int attemptNo, int used, int allowed, int remaining) {
        return new AttemptEligibilityDecision(false, code, retryAt, null, attemptNo, used, allowed, remaining);
    }
}
