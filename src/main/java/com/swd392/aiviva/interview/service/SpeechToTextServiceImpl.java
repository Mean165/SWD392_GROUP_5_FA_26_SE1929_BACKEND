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
}
