package com.swd392.aiviva.speech.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.stt.cost")
public class STTCostProperties {

    private BigDecimal realtimePricePerHour = BigDecimal.ZERO;
    private BigDecimal batchPricePerHour = BigDecimal.ZERO;
    private BigDecimal keytermsSurchargePerHour = BigDecimal.ZERO;

    public BigDecimal getRealtimePricePerHour() { return realtimePricePerHour; }
    public void setRealtimePricePerHour(BigDecimal value) { realtimePricePerHour = nonNegative(value); }
    public BigDecimal getBatchPricePerHour() { return batchPricePerHour; }
    public void setBatchPricePerHour(BigDecimal value) { batchPricePerHour = nonNegative(value); }
    public BigDecimal getKeytermsSurchargePerHour() { return keytermsSurchargePerHour; }
    public void setKeytermsSurchargePerHour(BigDecimal value) { keytermsSurchargePerHour = nonNegative(value); }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("STT rates must be non-negative.");
        return value;
    }
}
