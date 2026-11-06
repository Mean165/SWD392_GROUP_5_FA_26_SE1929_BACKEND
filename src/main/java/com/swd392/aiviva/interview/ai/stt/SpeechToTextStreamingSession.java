package com.swd392.aiviva.interview.ai.stt;

import java.time.Duration;

public interface SpeechToTextStreamingSession {

    void sendAudio(byte[] audioFrame);

    /** Closes the input stream and waits for outstanding final results. */
    String finish(Duration timeout);

    /** Closes the provider stream without waiting for a transcript. */
    void abort();
}
