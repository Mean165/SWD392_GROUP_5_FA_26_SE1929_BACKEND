package com.swd392.aiviva.speech.stt.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record STTResult(
        @NotBlank String provider,
        @NotBlank String model,
        @NotBlank String mode,
        String textRaw,
        List<STTWord> words,
        String languageCode,
        Double confidence,
        @NotBlank String confidenceSource,
        Long durationMs,
        Long latencyMs,
        List<String> keytermsUsed,
        String requestId,
        String error) {

    public STTResult {
        words = words == null ? List.of() : List.copyOf(words);
        keytermsUsed = keytermsUsed == null ? List.of() : List.copyOf(keytermsUsed);
    }

    public boolean usable() {
        return error == null && textRaw != null && !textRaw.isBlank();
    }
}
