package com.swd392.aiviva.speech.provider;

public class ZeroWeightProviderException extends RuntimeException {

    private final String errorCode;

    public ZeroWeightProviderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ZeroWeightProviderException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
