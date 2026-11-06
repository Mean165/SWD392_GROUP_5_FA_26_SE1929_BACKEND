package com.swd392.aiviva.evaluation.grading.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GradingApiExceptionHandlerTest {
    @Test
    void preservesContractErrorCodesAndStatuses() {
        GradingApiExceptionHandler handler = new GradingApiExceptionHandler();
        var locked = handler.handle(new GradingApiException(HttpStatus.LOCKED, "LOCKED_BY_OTHER", "claimed"));
        var incomplete = handler.handle(new GradingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INCOMPLETE_SCORES", "missing"));
        assertEquals(423, locked.getStatusCode().value());
        assertEquals("LOCKED_BY_OTHER", locked.getBody().getErrorCode());
        assertEquals(422, incomplete.getStatusCode().value());
        assertEquals("INCOMPLETE_SCORES", incomplete.getBody().getErrorCode());
    }
}
