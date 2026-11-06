package com.swd392.aiviva.evaluation.grading.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiiRedactorTest {
    @Test
    void removesCommonDirectIdentifiersBeforeModelPrompt() {
        String redacted = PiiRedactor.redact("Email sv@example.edu, phone 0912345678, code 12345678");
        assertFalse(redacted.contains("sv@example.edu"));
        assertFalse(redacted.contains("0912345678"));
        assertFalse(redacted.contains("12345678"));
        assertTrue(redacted.contains("[EMAIL]"));
        assertTrue(redacted.contains("[PHONE]"));
    }
}
