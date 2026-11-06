package com.swd392.aiviva.speech.stt.domain;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record STTEvent(
        @NotNull Kind kind,
        @NotNull UUID turnId,
        String text,
        List<STTWord> words,
        @NotNull Instant tsServer,
        Long providerLatencyMs,
        Double confidence,
        String errorCode,
        String errorMessage) {

    public STTEvent {
        words = words == null ? List.of() : List.copyOf(words);
    }

    public enum Kind {
        PARTIAL,
        COMMITTED,
        ERROR,
        CLOSED
    }
}
