package com.swd392.aiviva.common.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends RuntimeException {

    private final HttpStatus status;

    public ExternalServiceException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, null);
    }

    public ExternalServiceException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public ExternalServiceException(String message, HttpStatus status) {
        this(message, status, null);
    }

    private ExternalServiceException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
