package com.swd392.aiviva.common.exception;

public class InvalidRequestParameterException extends RuntimeException {
    private final String parameter;
    private final String errorCode;

    public InvalidRequestParameterException(String parameter, String errorCode, String message) {
        super(message);
        this.parameter = parameter;
        this.errorCode = errorCode;
    }

    public String getParameter() { return parameter; }
    public String getErrorCode() { return errorCode; }
}
