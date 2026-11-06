package com.swd392.aiviva.evaluation.grading.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum GradingStatus {
    WAITING_SESSION, AI_PENDING, AI_SCORING, AI_SCORED, AI_FAILED, IN_REVIEW, FINALIZED, RELEASED, REOPENED, VOIDED;

    private static final Map<GradingStatus, Set<GradingStatus>> TRANSITIONS = Map.of(
            WAITING_SESSION, EnumSet.of(AI_PENDING),
            AI_PENDING, EnumSet.of(AI_SCORING, IN_REVIEW),
            AI_SCORING, EnumSet.of(AI_SCORED, AI_FAILED, AI_PENDING),
            AI_SCORED, EnumSet.of(IN_REVIEW),
            AI_FAILED, EnumSet.of(AI_PENDING, IN_REVIEW),
            IN_REVIEW, EnumSet.of(FINALIZED),
            FINALIZED, EnumSet.of(RELEASED, REOPENED),
            RELEASED, EnumSet.of(REOPENED),
            REOPENED, EnumSet.of(IN_REVIEW));

    public boolean canTransitionTo(GradingStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public void requireTransitionTo(GradingStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("Invalid grading transition: " + this + " -> " + next);
        }
    }
}
