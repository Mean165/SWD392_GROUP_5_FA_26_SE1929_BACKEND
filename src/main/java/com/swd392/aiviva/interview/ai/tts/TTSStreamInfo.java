package com.swd392.aiviva.interview.ai.tts;

public record TTSStreamInfo(
        String contentType,
        String encoding,
        int sampleRate,
        int channels,
        long durationMillis) {
}
