package com.swd392.aiviva.speech.stt.domain;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record TurnTranscript(
        @NotNull UUID turnId,
        STTResult streaming,
        STTResult finalResult,
        String officialTextRaw,
        OfficialSource officialSource,
        String textNormalized,
        List<STTCorrection> corrections,
        boolean needsHumanReview,
        List<String> reviewReasons,
        STTReconciliation reconciliation) {

    public TurnTranscript {
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
    }

    public enum OfficialSource {
        FINAL,
        STREAMING
    }
}
