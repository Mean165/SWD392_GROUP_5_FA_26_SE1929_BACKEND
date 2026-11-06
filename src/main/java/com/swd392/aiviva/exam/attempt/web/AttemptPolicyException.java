package com.swd392.aiviva.exam.attempt.web;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

public class AttemptPolicyException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final OffsetDateTime retryAt;

    public AttemptPolicyException(HttpStatus status, String code, String message, OffsetDateTime retryAt) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAt = retryAt;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public OffsetDateTime getRetryAt() { return retryAt; }
}
