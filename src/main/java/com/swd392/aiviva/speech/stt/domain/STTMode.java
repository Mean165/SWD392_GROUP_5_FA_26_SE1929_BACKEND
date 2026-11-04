package com.swd392.aiviva.speech.stt.domain;

public enum STTMode {
    DUAL,
    STREAMING_ONLY,
    FINAL_ONLY;

    public static STTMode fromConfig(String value) {
        if (value == null || value.isBlank()) return STREAMING_ONLY;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "dual" -> DUAL;
            case "streaming_only" -> STREAMING_ONLY;
            case "final_only" -> FINAL_ONLY;
            default -> throw new IllegalArgumentException("STT mode must be streaming_only, final_only, or dual.");
        };
    }
}
