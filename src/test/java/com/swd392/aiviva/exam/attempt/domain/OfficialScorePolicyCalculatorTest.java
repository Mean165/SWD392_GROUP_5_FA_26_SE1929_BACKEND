package com.swd392.aiviva.exam.attempt.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficialScorePolicyCalculatorTest {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final List<OfficialScorePolicyCalculator.FinalizedAttemptScore> scores = List.of(
            new OfficialScorePolicyCalculator.FinalizedAttemptScore(first, 1, new BigDecimal("7.0")),
            new OfficialScorePolicyCalculator.FinalizedAttemptScore(second, 2, new BigDecimal("8.0")));

    @Test
    void latestFirstAndHighestSelectFinalizedAttemptsOnly() {
        assertEquals(second, result(ScorePolicy.LATEST).officialAttemptId());
        assertEquals(first, result(ScorePolicy.FIRST).officialAttemptId());
        assertEquals(second, result(ScorePolicy.HIGHEST).officialAttemptId());
    }

    @Test
    void averageUsesConfiguredRoundingAndManualPolicyNeverAutoSelects() {
        var average = result(ScorePolicy.AVERAGE);
        assertNull(average.officialAttemptId());
        assertEquals(new BigDecimal("7.5"), average.computedTotal());
        assertNull(result(ScorePolicy.LECTURER_CHOOSES).officialAttemptId());
        assertNull(result(ScorePolicy.LECTURER_CHOOSES).computedTotal());
    }

    private OfficialScorePolicyCalculator.Selection result(ScorePolicy policy) {
        return OfficialScorePolicyCalculator.select(policy, scores, new BigDecimal("0.5"));
    }
}
