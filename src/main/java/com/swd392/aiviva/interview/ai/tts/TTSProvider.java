package com.swd392.aiviva.interview.ai.tts;

import java.util.function.Consumer;

/** TTS provider boundary. Provider credentials and audio content must never be logged. */
public interface TTSProvider {

    default boolean isAvailable() {
        return true;
    }

    byte[] synthesize(String text);

    default byte[] synthesize(String text, String language) {
        return synthesize(text);
    }

    default byte[] synthesize(String text, String language, String responseFormat) {
        return synthesize(text, language);
    }

    default String providerName() {
        return getClass().getSimpleName();
    }

    default String defaultResponseFormat() {
        return "mp3";
    }

    default String cacheVariant(String language, String responseFormat) {
        return providerName() + "|" + language + "|" + responseFormat;
    }

    default TTSStreamInfo stream(String text, String language, Consumer<byte[]> chunkConsumer) {
        byte[] audio = synthesize(text, language);
        chunkConsumer.accept(audio);
        return new TTSStreamInfo(TtsAudioFormat.contentType(audio), "encoded-audio", 0, 0, 0);
    }

    default TTSStreamInfo streamFormat() {
        return new TTSStreamInfo("audio/mpeg", "encoded-audio", 0, 0, 0);
    }
}
