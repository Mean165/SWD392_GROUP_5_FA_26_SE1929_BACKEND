package com.swd392.aiviva.speech.stt.domain;

public record FluencySignals(
        Long responseLatencyMs,
        Long speechDurationMs,
        Double wordsPerMinute,
        Integer pauseCount,
        Long longestPauseMs,
        Integer fillerCount) {
}
