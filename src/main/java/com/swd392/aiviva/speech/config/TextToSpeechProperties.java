package com.swd392.aiviva.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.tts")
public class TextToSpeechProperties {

    private String provider = "local";
    private String defaultLanguage = "vi";
    private int cacheMaxEntries = 512;
    private final Local local = new Local();

    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String value) { defaultLanguage = value; }
    public int getCacheMaxEntries() { return cacheMaxEntries; }
    public void setCacheMaxEntries(int value) { cacheMaxEntries = value; }
    public Local getLocal() { return local; }

    public static class Local {
        private String engine = "korva";
        private String voiceVi = "khanh_vy";
        private String voiceEn = "en_US-lessac-medium";
        private double speed = 1.0;
        private String cacheDirectory = "/models/tts-cache";
        private int maxTextLength = 4000;

        public String getEngine() { return engine; }
        public void setEngine(String value) { engine = value; }
        public String getVoiceVi() { return voiceVi; }
        public void setVoiceVi(String value) { voiceVi = value; }
        public String getVoiceEn() { return voiceEn; }
        public void setVoiceEn(String value) { voiceEn = value; }
        public double getSpeed() { return speed; }
        public void setSpeed(double value) { speed = value; }
        public String getCacheDirectory() { return cacheDirectory; }
        public void setCacheDirectory(String value) { cacheDirectory = value; }
        public int getMaxTextLength() { return maxTextLength; }
        public void setMaxTextLength(int value) { maxTextLength = value; }
    }
}
