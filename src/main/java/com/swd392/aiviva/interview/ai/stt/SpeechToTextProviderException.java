package com.swd392.aiviva.interview.ai.stt;

public class SpeechToTextProviderException extends RuntimeException {

    private final String errorCode;

    public SpeechToTextProviderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SpeechToTextProviderException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
