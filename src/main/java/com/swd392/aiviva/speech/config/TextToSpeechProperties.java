package com.swd392.aiviva.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.tts")
public class TextToSpeechProperties {

    private String provider = "openai";
    private String defaultLanguage = "vi";
    private int cacheMaxEntries = 512;
    private final ZeroWeight zeroWeight = new ZeroWeight();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public int getCacheMaxEntries() { return cacheMaxEntries; }
    public void setCacheMaxEntries(int cacheMaxEntries) { this.cacheMaxEntries = cacheMaxEntries; }
    public ZeroWeight getZeroWeight() { return zeroWeight; }

    public static class ZeroWeight {
        private String apiKey = "";
        private String baseUrl = "https://api.zeroweight.ai";
        private String voiceId = "ngocanh";
        private double speed = 1.0;
        private boolean normalizeText = true;
        private boolean internalNormalizationEnabled;
        private int timeoutMillis = 20_000;
        private long creditBudget;
        private long creditWarningRemaining = 1_000;
        private int maxAudioBytes = 20 * 1024 * 1024;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public boolean isNormalizeText() { return normalizeText; }
        public void setNormalizeText(boolean normalizeText) { this.normalizeText = normalizeText; }
        public boolean isInternalNormalizationEnabled() { return internalNormalizationEnabled; }
        public void setInternalNormalizationEnabled(boolean internalNormalizationEnabled) {
            this.internalNormalizationEnabled = internalNormalizationEnabled;
        }
        public int getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
        public long getCreditBudget() { return creditBudget; }
        public void setCreditBudget(long creditBudget) { this.creditBudget = creditBudget; }
        public long getCreditWarningRemaining() { return creditWarningRemaining; }
        public void setCreditWarningRemaining(long creditWarningRemaining) { this.creditWarningRemaining = creditWarningRemaining; }
        public int getMaxAudioBytes() { return maxAudioBytes; }
        public void setMaxAudioBytes(int maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    }
}
