package com.swd392.aiviva.devicecheck.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "device-check")
public class DeviceCheckProperties {

    private int maxAudioBytes = 2 * 1024 * 1024;
    private int maxDurationSeconds = 15;
    private List<String> acceptedMimeTypes = List.of(
            "audio/webm", "audio/ogg", "audio/wav", "audio/x-wav",
            "audio/mpeg", "audio/mp4", "audio/m4a");
    private List<String> languages = List.of("vi", "en");
    private String defaultLanguage = "vi";
    private int recommendedRecordingSeconds = 5;
    private Map<String, String> sample = Map.of(
            "vi", "Tôi sẵn sàng bắt đầu bài thi vấn đáp",
            "en", "I am ready to start the oral exam");
    private Map<String, String> ttsSample = Map.of(
            "vi", "Xin chào, đây là kiểm tra âm thanh. Nếu bạn nghe rõ giọng nói này, loa của bạn hoạt động tốt.",
            "en", "Hello, this is an audio check. If you can hear this voice clearly, your speakers are working.");
    private RateLimit rateLimit = new RateLimit();

    public int getMaxAudioBytes() {
        return maxAudioBytes;
    }

    public void setMaxAudioBytes(int maxAudioBytes) {
        this.maxAudioBytes = maxAudioBytes;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public List<String> getAcceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    public void setAcceptedMimeTypes(List<String> acceptedMimeTypes) {
        this.acceptedMimeTypes = acceptedMimeTypes;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public int getRecommendedRecordingSeconds() {
        return recommendedRecordingSeconds;
    }

    public void setRecommendedRecordingSeconds(int recommendedRecordingSeconds) {
        this.recommendedRecordingSeconds = recommendedRecordingSeconds;
    }

    public Map<String, String> getSample() {
        return sample;
    }

    public void setSample(Map<String, String> sample) {
        this.sample = sample;
    }

    public Map<String, String> getTtsSample() {
        return ttsSample;
    }

    public void setTtsSample(Map<String, String> ttsSample) {
        this.ttsSample = ttsSample;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {
        private int requestsPerMinute = 10;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
