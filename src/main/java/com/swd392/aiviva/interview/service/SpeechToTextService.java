package com.swd392.aiviva.interview.service;

public interface SpeechToTextService {

    String transcribe(String audioDataUrl);

    default String transcribe(String audioDataUrl, String language) {
        return transcribe(audioDataUrl);
    }

    default String transcribe(byte[] audioBytes, String filename, String language) {
        throw new UnsupportedOperationException("Byte-based audio transcription is not supported.");
    }
}
