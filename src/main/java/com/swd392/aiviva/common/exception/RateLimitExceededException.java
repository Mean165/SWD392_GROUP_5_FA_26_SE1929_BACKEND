package com.swd392.aiviva.common.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        this(retryAfterSeconds, "Device-check request limit exceeded. Retry later.");
    }

    public RateLimitExceededException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
