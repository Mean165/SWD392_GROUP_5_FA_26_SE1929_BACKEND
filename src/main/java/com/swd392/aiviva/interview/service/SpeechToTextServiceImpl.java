package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderAdapter;
import org.springframework.stereotype.Service;

@Service
public class SpeechToTextServiceImpl implements SpeechToTextService {

    private final SpeechToTextProviderAdapter sttProviderAdapter;

    public SpeechToTextServiceImpl(SpeechToTextProviderAdapter sttProviderAdapter) {
        this.sttProviderAdapter = sttProviderAdapter;
    }

    @Override
    public String transcribe(String audioDataUrl) {
        return sttProviderAdapter.transcribe(audioDataUrl);
    }

    @Override
    public String transcribe(String audioDataUrl, String language) {
        return sttProviderAdapter.transcribe(audioDataUrl, language);
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename, String language) {
        return sttProviderAdapter.transcribe(audioBytes, filename, language);
    }
}
