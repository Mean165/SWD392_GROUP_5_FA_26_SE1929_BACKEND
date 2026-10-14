package com.swd392.aiviva.interview.ai.stt;

import com.swd392.aiviva.common.config.OpenAiConfig;
import org.springframework.stereotype.Component;

@Component
public class OpenAiWhisperSttProviderAdapter implements SpeechToTextProviderAdapter {

    private final OpenAiConfig openAiConfig;

    public OpenAiWhisperSttProviderAdapter(OpenAiConfig openAiConfig) {
        this.openAiConfig = openAiConfig;
    }

    @Override
    public String transcribe(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return "";
        }
        // In full production, this adapter posts the audio binary / multipart to OpenAI's https://api.openai.com/v1/audio/transcriptions
        // For development fallback or when passed audio URL:
        return "Student transcribed response for: " + audioUrl;
    }
}
