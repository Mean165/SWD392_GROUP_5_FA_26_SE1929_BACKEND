package com.swd392.aiviva.evaluation.grading.web;

import org.springframework.http.HttpStatus;

public class GradingApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public GradingApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
