package com.swd392.aiviva.question.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeminiApiExceptionTest {
    @Test
    void distinguishesProviderQuotaFromRateLimitAndIncludesRetryDelay() {
        GeminiApiException rateLimit = new GeminiApiException(429, 8, "RESOURCE_EXHAUSTED: requests per minute");
        GeminiApiException quota = new GeminiApiException(429, 8, "Quota exceeded for this project");

        assertTrue(rateLimit.retryable());
        assertEquals(8, rateLimit.retryAfterSeconds());
        assertTrue(rateLimit.userMessage().contains("giới hạn tốc độ"));
        assertFalse(quota.retryable());
        assertTrue(quota.userMessage().contains("hết quota"));
    }

    @Test
    void failsFastWhenGeminiIsSelectedWithoutAnApiKey() {
        assertThrows(IllegalStateException.class,
                () -> new GeminiQuestionAiAdapter(new ObjectMapper(), " ", "gemini-3.8-flash",
                        "gemini-embedding-001", 45));
    }
}
