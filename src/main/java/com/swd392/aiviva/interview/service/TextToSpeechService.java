package com.swd392.aiviva.interview.service;

public interface TextToSpeechService {

    byte[] synthesize(String text);

    default byte[] synthesize(String text, String language) {
        return synthesize(text);
    }

    default byte[] synthesize(String text, String language, String responseFormat) {
        return synthesize(text, language);
    }
}
