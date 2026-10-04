package com.swd392.aiviva.interview.service;

import org.springframework.stereotype.Service;

@Service
public class TextToSpeechServiceImpl implements TextToSpeechService {

    @Override
    public byte[] synthesize(String text) {
        // TODO: Integrate TTS provider adapter.
        return new byte[0];
    }
}

