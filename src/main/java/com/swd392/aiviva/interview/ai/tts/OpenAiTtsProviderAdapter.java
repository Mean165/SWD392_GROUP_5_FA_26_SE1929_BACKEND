package com.swd392.aiviva.interview.ai.tts;

import com.swd392.aiviva.common.config.OpenAiConfig;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTtsProviderAdapter implements TextToSpeechProviderAdapter {

    private final OpenAiConfig openAiConfig;

    public OpenAiTtsProviderAdapter(OpenAiConfig openAiConfig) {
        this.openAiConfig = openAiConfig;
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        // In full production, this calls OpenAI's https://api.openai.com/v1/audio/speech
        // For development fallback:
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
