package com.swd392.aiviva.exam.attempt.domain;

import com.swd392.aiviva.evaluation.grading.domain.GradingScoreCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class OfficialScorePolicyCalculator {
    private OfficialScorePolicyCalculator() { }

    public static Selection select(ScorePolicy policy, List<FinalizedAttemptScore> attempts,
            BigDecimal roundingStep) {
        if (attempts == null || attempts.isEmpty()) return new Selection(null, null);
        return switch (policy) {
            case LATEST -> attempts.stream().max(Comparator.comparingInt(FinalizedAttemptScore::attemptNo))
                    .map(row -> new Selection(row.attemptId(), null)).orElseGet(() -> new Selection(null, null));
            case FIRST -> attempts.stream().min(Comparator.comparingInt(FinalizedAttemptScore::attemptNo))
                    .map(row -> new Selection(row.attemptId(), null)).orElseGet(() -> new Selection(null, null));
            case HIGHEST -> attempts.stream().max(Comparator.comparing(FinalizedAttemptScore::total)
                            .thenComparingInt(row -> -row.attemptNo()))
                    .map(row -> new Selection(row.attemptId(), null)).orElseGet(() -> new Selection(null, null));
            case AVERAGE -> {
                BigDecimal average = attempts.stream().map(FinalizedAttemptScore::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(attempts.size()), 8, RoundingMode.HALF_UP);
                yield new Selection(null, GradingScoreCalculator.applyStep(average, BigDecimal.TEN, roundingStep));
            }
            case LECTURER_CHOOSES -> new Selection(null, null);
        };
    }

    public record FinalizedAttemptScore(UUID attemptId, int attemptNo, BigDecimal total) { }
    public record Selection(UUID officialAttemptId, BigDecimal computedTotal) { }
}
