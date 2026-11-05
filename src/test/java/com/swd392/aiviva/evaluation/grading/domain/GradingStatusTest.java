package com.swd392.aiviva.evaluation.grading.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GradingStatusTest {
    @Test
    void onlyLecturerReviewPathCanReachFinalized() {
        assertFalse(GradingStatus.AI_SCORED.canTransitionTo(GradingStatus.FINALIZED));
        assertFalse(GradingStatus.AI_FAILED.canTransitionTo(GradingStatus.FINALIZED));
        assertFalse(GradingStatus.AI_SCORING.canTransitionTo(GradingStatus.FINALIZED));
        assertTrue(GradingStatus.IN_REVIEW.canTransitionTo(GradingStatus.FINALIZED));
        assertThrows(IllegalStateException.class, () -> GradingStatus.AI_SCORED.requireTransitionTo(GradingStatus.FINALIZED));
    }

    @Test
    void reopeningPreservesFinalizedHistoryAndReturnsToReview() {
        assertTrue(GradingStatus.FINALIZED.canTransitionTo(GradingStatus.REOPENED));
        assertTrue(GradingStatus.RELEASED.canTransitionTo(GradingStatus.REOPENED));
        assertTrue(GradingStatus.REOPENED.canTransitionTo(GradingStatus.IN_REVIEW));
    }
}
