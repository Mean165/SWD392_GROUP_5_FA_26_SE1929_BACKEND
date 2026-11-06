package com.swd392.aiviva.evaluation.grading.ai;

public class DisabledGradingLlmProvider implements GradingLlmProvider {
    @Override public String providerName() { return "disabled"; }
    @Override public String modelName() { return ""; }
    @Override public boolean isReady() { return false; }
    @Override public String reason() { return "AI grading is disabled"; }
    @Override public String complete(String systemPrompt, String userPrompt, double temperature) {
        throw new IllegalStateException("AI grading is disabled");
    }
}
