package com.swd392.aiviva.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai.api")
@Getter
@Setter
public class OpenAiConfig {

    private String key;
    private String url = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-4o-mini";
    private String ttsUrl = "https://api.openai.com/v1/audio/speech";
    private String sttUrl = "https://api.openai.com/v1/audio/transcriptions";
    private String ttsModel = "tts-1";
    private String sttModel = "gpt-4o-transcribe";
    private String voice = "alloy";
    private String voiceVi;
    private String voiceEn;
    private String language = "vi";
    private String responseFormat = "mp3";
    private double speed = 1.0;
    private int timeout = 30000;
    private int maxTextLength = 4000;

    public boolean isConfigured() {
        return key != null && !key.isBlank();
    }

    public String getVoiceForLanguage(String language) {
        String configured = "vi".equalsIgnoreCase(language) ? voiceVi : voiceEn;
        return configured == null || configured.isBlank() ? voice : configured;
    }
}
