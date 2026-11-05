package com.swd392.aiviva.exam.attempt.web;

import com.swd392.aiviva.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttemptPolicyExceptionHandler {
    @ExceptionHandler(AttemptPolicyException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AttemptPolicyException exception) {
        String message = exception.getRetryAt() == null ? exception.getMessage()
                : exception.getMessage() + " Retry at " + exception.getRetryAt();
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(message, exception.getCode()));
    }
}
