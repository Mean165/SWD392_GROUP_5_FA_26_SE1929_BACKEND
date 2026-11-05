package com.swd392.aiviva.evaluation.grading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GradingScoreCalculator {
    private GradingScoreCalculator() { }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("At least one sample is required");
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.forEach(GradingScoreCalculator::requireFraction);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
    }

    public static BigDecimal variance(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("At least one sample is required");
        values.forEach(GradingScoreCalculator::requireFraction);
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 12, RoundingMode.HALF_UP);
        BigDecimal squared = values.stream().map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return squared.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }

    public static BigDecimal applyStep(BigDecimal score, BigDecimal maxScore, BigDecimal step) {
        if (score == null || maxScore == null || step == null || maxScore.signum() <= 0 || step.signum() <= 0) {
            throw new IllegalArgumentException("Score, positive max score, and positive step are required");
        }
        BigDecimal clamped = score.max(BigDecimal.ZERO).min(maxScore);
        return clamped.divide(step, 0, RoundingMode.HALF_UP).multiply(step).min(maxScore);
    }

    public static BigDecimal scoreFromFraction(BigDecimal fraction, BigDecimal maxScore, BigDecimal step) {
        requireFraction(fraction);
        return applyStep(fraction.multiply(maxScore), maxScore, step);
    }

    private static void requireFraction(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Score fractions must be between 0 and 1");
        }
    }
}
