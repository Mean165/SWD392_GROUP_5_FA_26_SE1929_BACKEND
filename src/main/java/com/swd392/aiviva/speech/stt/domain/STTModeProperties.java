package com.swd392.aiviva.speech.stt.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.stt.pipeline")
public class STTModeProperties {

    private String mode = "realtime_only";
    private double reconcileThreshold = 0.95;
    private double minimumConfidence = 0.55;
    private double glossaryFixMinimumScore = 0.92;
    private long pauseMinimumMs = 700;
    private int maxAudioSeconds = 180;
    private long batchTimeoutSeconds = 30;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public STTMode getParsedMode() { return STTMode.fromConfig(mode); }
    public double getReconcileThreshold() { return reconcileThreshold; }
    public void setReconcileThreshold(double value) { this.reconcileThreshold = value; }
    public double getMinimumConfidence() { return minimumConfidence; }
    public void setMinimumConfidence(double value) { this.minimumConfidence = value; }
    public double getGlossaryFixMinimumScore() { return glossaryFixMinimumScore; }
    public void setGlossaryFixMinimumScore(double value) { this.glossaryFixMinimumScore = value; }
    public long getPauseMinimumMs() { return pauseMinimumMs; }
    public void setPauseMinimumMs(long value) { this.pauseMinimumMs = value; }
    public int getMaxAudioSeconds() { return maxAudioSeconds; }
    public void setMaxAudioSeconds(int value) { this.maxAudioSeconds = value; }
    public long getBatchTimeoutSeconds() { return batchTimeoutSeconds; }
    public void setBatchTimeoutSeconds(long value) { this.batchTimeoutSeconds = value; }
}
