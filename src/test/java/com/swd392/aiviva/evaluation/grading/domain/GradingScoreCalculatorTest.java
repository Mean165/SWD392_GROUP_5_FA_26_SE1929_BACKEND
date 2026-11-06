package com.swd392.aiviva.evaluation.grading.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradingScoreCalculatorTest {
    @Test
    void medianAndPopulationVarianceAreDeterministic() {
        assertEquals(new BigDecimal("0.8"), GradingScoreCalculator.median(List.of(
                new BigDecimal("0.2"), new BigDecimal("0.8"), new BigDecimal("1.0"))));
        assertEquals(new BigDecimal("0.11555556"), GradingScoreCalculator.variance(List.of(
                new BigDecimal("0.2"), new BigDecimal("0.8"), new BigDecimal("1.0"))));
    }

    @Test
    void clampsAndRoundsCodeCalculatedScore() {
        assertEquals(new BigDecimal("7.5"), GradingScoreCalculator.scoreFromFraction(
                new BigDecimal("0.76"), new BigDecimal("10"), new BigDecimal("0.5")));
        assertThrows(IllegalArgumentException.class, () -> GradingScoreCalculator.scoreFromFraction(
                new BigDecimal("1.01"), new BigDecimal("10"), new BigDecimal("0.5")));
    }
}
