package com.swd392.aiviva.speech.stt.domain;

import jakarta.validation.constraints.NotBlank;

public record STTWord(
        @NotBlank String text,
        Long startMs,
        Long endMs,
        Double confidence,
        String type,
        String speaker) {
}
