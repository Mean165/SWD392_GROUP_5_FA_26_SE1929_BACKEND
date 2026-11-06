package com.swd392.aiviva.evaluation.grading.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grading.ai")
public class GradingAiProperties {
    private String provider = "disabled";
    private String model = "local-model";
    private String baseUrl = "http://127.0.0.1:11434/v1";
    private String apiKey = "";
    private int timeoutSeconds = 90;
    private int samples = 3;
    private boolean allowRemote;
    private boolean includeSignals;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getSamples() { return samples; }
    public void setSamples(int samples) { this.samples = samples; }
    public boolean isAllowRemote() { return allowRemote; }
    public void setAllowRemote(boolean allowRemote) { this.allowRemote = allowRemote; }
    public boolean isIncludeSignals() { return includeSignals; }
    public void setIncludeSignals(boolean includeSignals) { this.includeSignals = includeSignals; }
}
