package com.swd392.aiviva.interview.ai.stt;

public interface SpeechToTextProviderAdapter {

    String transcribe(String audioUrl);

    default String transcribe(String audioUrl, String language) {
        return transcribe(audioUrl);
    }

    default String transcribe(byte[] audioBytes, String filename, String language) {
        throw new UnsupportedOperationException("Byte-based audio transcription is not supported.");
    }
}
