package com.swd392.aiviva.evaluation.grading.ai;

public interface GradingLlmProvider {
    String providerName();
    String modelName();
    boolean isReady();
    String reason();
    String complete(String systemPrompt, String userPrompt, double temperature);
}
