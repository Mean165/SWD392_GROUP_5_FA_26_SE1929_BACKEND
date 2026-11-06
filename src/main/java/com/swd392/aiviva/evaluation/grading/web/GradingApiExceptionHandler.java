package com.swd392.aiviva.evaluation.grading.web;

import com.swd392.aiviva.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GradingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GradingApiExceptionHandler {
    @ExceptionHandler(GradingApiException.class)
    public ResponseEntity<ApiResponse<Void>> handle(GradingApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(exception.getMessage(), exception.getCode()));
    }
}
