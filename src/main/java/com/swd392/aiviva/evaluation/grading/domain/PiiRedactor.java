package com.swd392.aiviva.evaluation.grading.domain;

import java.util.regex.Pattern;

public final class PiiRedactor {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)(?:[ .()-]*\\d){8,10}(?!\\d)");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");

    private PiiRedactor() { }

    public static String redact(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        return LONG_NUMBER.matcher(PHONE.matcher(EMAIL.matcher(text).replaceAll("[EMAIL]")).replaceAll("[PHONE]")).replaceAll("[ID]");
    }
}
