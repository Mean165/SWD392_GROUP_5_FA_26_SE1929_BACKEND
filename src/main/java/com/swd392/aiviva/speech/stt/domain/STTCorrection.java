package com.swd392.aiviva.speech.stt.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record STTCorrection(
        @NotBlank String rawSpan,
        @NotBlank String corrected,
        @NotBlank String rule,
        @NotNull Double score) {
}
