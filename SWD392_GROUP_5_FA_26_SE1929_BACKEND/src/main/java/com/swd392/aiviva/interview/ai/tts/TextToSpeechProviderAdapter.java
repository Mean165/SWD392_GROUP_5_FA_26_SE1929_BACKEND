package com.swd392.aiviva.interview.ai.tts;

public interface TextToSpeechProviderAdapter {

    byte[] synthesize(String text);
}

