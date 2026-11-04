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
        String error,
        String textNormalized,
        List<String> warnings) {

    public STTResult(String provider, String model, String mode, String textRaw, List<STTWord> words,
            String languageCode, Double confidence, String confidenceSource, Long durationMs,
            Long latencyMs, List<String> keytermsUsed, String requestId, String error) {
        this(provider, model, mode, textRaw, words, languageCode, confidence, confidenceSource,
                durationMs, latencyMs, keytermsUsed, requestId, error, null, List.of());
    }

    public STTResult {
        words = words == null ? List.of() : List.copyOf(words);
        keytermsUsed = keytermsUsed == null ? List.of() : List.copyOf(keytermsUsed);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean usable() {
        return error == null && textRaw != null && !textRaw.isBlank();
    }
}
