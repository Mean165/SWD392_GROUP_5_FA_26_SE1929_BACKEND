package com.swd392.aiviva.interview.service;

import org.springframework.stereotype.Service;

@Service
public class SpeechToTextServiceImpl implements SpeechToTextService {

    @Override
    public String transcribe(String audioDataUrl) {
        // TODO: Integrate STT provider adapter.
        return "transcribed-text";
    }
}

