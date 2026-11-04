package com.swd392.aiviva.speech.stt.domain;

public enum STTMode {
    DUAL,
    REALTIME_ONLY,
    BATCH_ONLY;

    public static STTMode fromConfig(String value) {
        if (value == null || value.isBlank()) return REALTIME_ONLY;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "dual" -> DUAL;
            case "realtime_only" -> REALTIME_ONLY;
            case "batch_only" -> BATCH_ONLY;
            default -> throw new IllegalArgumentException("STT mode must be dual, realtime_only, or batch_only.");
        };
    }
}
