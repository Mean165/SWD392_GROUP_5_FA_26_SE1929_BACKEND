package com.swd392.aiviva.question.enums;

import java.util.Locale;

public enum BloomLevel {
    REMEMBER,
    UNDERSTAND,
    APPLY,
    ANALYZE;

    public static String normalizeLegacyValue(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "EVALUATE".equals(normalized) ? ANALYZE.name() : normalized;
    }
}

