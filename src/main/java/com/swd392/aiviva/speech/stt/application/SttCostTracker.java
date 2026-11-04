package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.speech.config.STTCostProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** In-process estimate only; vendor-specific rates must come from current account configuration. */
@Component
public class SttCostTracker {

    private final LongAdder realtimeAudioMillis = new LongAdder();
    private final LongAdder batchAudioMillis = new LongAdder();
    private final LongAdder keytermAudioMillis = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final STTCostProperties costProperties;

    public SttCostTracker(STTCostProperties costProperties) {
        this.costProperties = costProperties;
    }

    public void record(String mode, long durationMs, boolean keytermsEnabled, boolean failed) {
        long duration = Math.max(0, durationMs);
        String normalized = mode == null ? "" : mode.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("realtime") || normalized.equals("streaming")) realtimeAudioMillis.add(duration);
        if (normalized.contains("batch") || normalized.equals("final")) batchAudioMillis.add(duration);
        if (keytermsEnabled) keytermAudioMillis.add(duration);
        if (failed) errors.increment();
    }

    public Snapshot snapshot(Pricing pricing) {
        BigDecimal realtime = cost(realtimeAudioMillis.sum(), pricing.realtimePerHour());
        BigDecimal batch = cost(batchAudioMillis.sum(), pricing.batchPerHour());
        BigDecimal keyterms = cost(keytermAudioMillis.sum(), pricing.keytermsSurchargePerHour());
        return new Snapshot(realtimeAudioMillis.sum(), batchAudioMillis.sum(), keytermAudioMillis.sum(),
                realtime, batch, keyterms, realtime.add(batch).add(keyterms), errors.sum());
    }

    public Snapshot snapshot() {
        return snapshot(new Pricing(costProperties.getRealtimePricePerHour(),
                costProperties.getBatchPricePerHour(), costProperties.getKeytermsSurchargePerHour()));
    }

    private BigDecimal cost(long millis, BigDecimal perHour) {
        if (perHour == null || millis <= 0) return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        return perHour.multiply(BigDecimal.valueOf(millis))
                .divide(BigDecimal.valueOf(3_600_000L), 6, RoundingMode.HALF_UP);
    }

    public record Pricing(BigDecimal realtimePerHour, BigDecimal batchPerHour, BigDecimal keytermsSurchargePerHour) {
        public Pricing {
            realtimePerHour = nonNegative(realtimePerHour);
            batchPerHour = nonNegative(batchPerHour);
            keytermsSurchargePerHour = nonNegative(keytermsSurchargePerHour);
        }

        private static BigDecimal nonNegative(BigDecimal value) {
            if (value != null && value.signum() < 0) throw new IllegalArgumentException("STT price must be non-negative.");
            return value;
        }
    }

    public record Snapshot(long realtimeAudioMillis, long batchAudioMillis, long keytermsAudioMillis,
            BigDecimal realtimeCost, BigDecimal batchCost, BigDecimal keytermsCost,
            BigDecimal estimatedTotalCost, long errors) {
    }
}
