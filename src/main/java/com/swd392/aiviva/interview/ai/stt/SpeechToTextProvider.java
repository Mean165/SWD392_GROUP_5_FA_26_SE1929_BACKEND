package com.swd392.aiviva.interview.ai.stt;

/** Provider boundary for live STT. Audio and transcripts must not be logged by implementations. */
public interface SpeechToTextProvider {

    boolean isAvailable();

    default String readinessErrorCode() {
        return "STT_NOT_READY";
    }

    String providerName();

    SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, SpeechToTextResultListener listener);

    default SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, java.util.List<String> keyterms, SpeechToTextResultListener listener) {
        return openStreamingSession(language, sampleRate, listener);
    }
}
