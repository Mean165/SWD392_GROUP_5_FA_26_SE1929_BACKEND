package com.swd392.aiviva.interview.ai.stt;

public interface SpeechToTextResultListener {

    void onPartial(String text);

    void onFinal(String text, double confidence);

    void onError(Throwable error);
}
