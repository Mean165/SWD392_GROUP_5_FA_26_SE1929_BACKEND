package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.tts.TextToSpeechProviderAdapter;
import org.springframework.stereotype.Service;

@Service
public class TextToSpeechServiceImpl implements TextToSpeechService {

    private final TextToSpeechProviderAdapter ttsProviderAdapter;

    public TextToSpeechServiceImpl(TextToSpeechProviderAdapter ttsProviderAdapter) {
        this.ttsProviderAdapter = ttsProviderAdapter;
    }

    @Override
    public byte[] synthesize(String text) {
        return ttsProviderAdapter.synthesize(text);
    }

    @Override
    public byte[] synthesize(String text, String language) {
        return ttsProviderAdapter.synthesize(text, language);
    }

    @Override
    public byte[] synthesize(String text, String language, String responseFormat) {
        return ttsProviderAdapter.synthesize(text, language, responseFormat);
    }
}
