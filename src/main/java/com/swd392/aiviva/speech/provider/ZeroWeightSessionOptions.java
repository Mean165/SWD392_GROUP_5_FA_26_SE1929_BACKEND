package com.swd392.aiviva.speech.provider;

public record ZeroWeightSessionOptions(
        String apiKey,
        String baseUrl,
        String voiceId,
        double speed,
        boolean normalizeText,
        int timeoutMillis,
        int maxAudioBytes) {
}
