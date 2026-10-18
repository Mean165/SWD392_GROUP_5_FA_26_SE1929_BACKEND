package com.swd392.aiviva.devicecheck.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TextMatchUtil {

    private TextMatchUtil() {
    }

    public static double score(String expected, String actual) {
        String expectedText = normalize(expected);
        String actualText = normalize(actual);
        if (expectedText.isEmpty() || actualText.isEmpty()) {
            return 0.0;
        }
        double score = Math.max(scoreNormalized(expectedText, actualText),
                scoreNormalized(removeVietnameseMarks(expectedText), removeVietnameseMarks(actualText)));
        return Math.max(0.0, Math.min(1.0, score));
    }

    private static double scoreNormalized(String expected, String actual) {
        String[] expectedTokens = expected.split(" ");
        String[] actualTokens = actual.split(" ");
        Map<String, Integer> available = new HashMap<>();
        for (String token : actualTokens) {
            available.merge(token, 1, Integer::sum);
        }
        int matches = 0;
        for (String token : expectedTokens) {
            int count = available.getOrDefault(token, 0);
            if (count > 0) {
                matches++;
                available.put(token, count - 1);
            }
        }
        double tokenRate = (double) matches / expectedTokens.length;
        int maxLength = Math.max(expected.length(), actual.length());
        double editSimilarity = 1.0 - (double) levenshteinDistance(expected, actual) / maxLength;
        return 0.6 * tokenRate + 0.4 * editSimilarity;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String removeVietnameseMarks(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd');
    }

    private static int levenshteinDistance(String left, String right) {
        if (left.length() < right.length()) {
            String swap = left;
            left = right;
            right = swap;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitutionCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
