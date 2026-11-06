package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.tts.TTSStreamInfo;
import java.util.function.Consumer;

public interface TextToSpeechService {

    byte[] synthesize(String text);

    default byte[] synthesize(String text, String language) {
        return synthesize(text);
    }

    default byte[] synthesize(String text, String language, String responseFormat) {
        return synthesize(text, language);
    }

    default String cacheKey(String text, String language, String responseFormat) {
        return String.join("|", text == null ? "" : text, language == null ? "" : language,
                responseFormat == null ? "" : responseFormat);
    }

    default void prewarm(String text, String language) {
        // Providers may warm a cache asynchronously.
    }

    default TTSStreamInfo stream(String text, String language, Consumer<byte[]> chunkConsumer) {
        byte[] audio = synthesize(text, language);
        chunkConsumer.accept(audio);
        return new TTSStreamInfo(com.swd392.aiviva.interview.ai.tts.TtsAudioFormat.contentType(audio),
                "encoded-audio", 0, 0, 0);
    }

    default TTSStreamInfo streamFormat() {
        return new TTSStreamInfo("audio/mpeg", "encoded-audio", 0, 0, 0);
    }
}
