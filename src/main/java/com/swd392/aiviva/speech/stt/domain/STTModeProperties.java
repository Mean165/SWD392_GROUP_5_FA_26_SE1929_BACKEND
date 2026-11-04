package com.swd392.aiviva.speech.stt.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.stt.pipeline")
public class STTModeProperties {

    private String mode = "streaming_only";
    private double reconcileThreshold = 0.95;
    private double minimumConfidence = 0.55;
    private double glossaryFixMinimumScore = 0.92;
    private long pauseMinimumMs = 700;
    private int maxAudioSeconds = 180;
    private long finalTimeoutSeconds = 30;
    private int maxRepeatPerTurn = 2;
    private int initialPromptMaxTokens = 160;

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
    public long getFinalTimeoutSeconds() { return finalTimeoutSeconds; }
    public void setFinalTimeoutSeconds(long value) { this.finalTimeoutSeconds = value; }
    public int getMaxRepeatPerTurn() { return maxRepeatPerTurn; }
    public void setMaxRepeatPerTurn(int value) { this.maxRepeatPerTurn = value; }
    public int getInitialPromptMaxTokens() { return initialPromptMaxTokens; }
    public void setInitialPromptMaxTokens(int value) { this.initialPromptMaxTokens = value; }
}
