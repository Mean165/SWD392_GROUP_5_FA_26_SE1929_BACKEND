package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeytermSelectorTest {

    private final KeytermSelector selector = new KeytermSelector();

    @Test
    void ranksSourcesDeduplicatesNfcCaseInsensitivelyAndEnforcesRealtimeLimits() {
        var result = selector.select(new KeytermSelector.Sources(
                List.of("Cafe\u0301", "CAFE\u0301"), List.of("Application"), List.of("Course"),
                List.of("RAG term"), List.of("Previous term")), KeytermSelector.realtimeDefaults());

        assertEquals(List.of("Café", "Application", "Course", "RAG term", "Previous term"), result.terms());
    }

    @Test
    void dropsInvalidAndLongTermsOrUsesAConfiguredAbbreviation() {
        var result = selector.select(new KeytermSelector.Sources(
                List.of("bad {term}", "supercalifragilisticexpialidocious", "Java Virtual Machine"),
                List.of(), List.of(), List.of(), List.of()),
                KeytermSelector.realtimeDefaults(), Map.of("supercalifragilisticexpialidocious", "super JVM"));

        assertEquals(List.of("bad {term}", "super JVM", "Java Virtual Machine"), result.terms());
        assertEquals(0, result.droppedInvalid());
        assertEquals(0, result.droppedOverLength());
    }

    @Test
    void appliesBatchFiveWordLimitAndVendorCharacterLimits() {
        var result = selector.select(new KeytermSelector.Sources(
                List.of("one two three four five six", "one two three"), List.of("bad<term>"),
                List.of(), List.of(), List.of()), KeytermSelector.batchDefaults());

        assertEquals(List.of("one two three"), result.terms());
        assertTrue(result.droppedOverLength() + result.droppedInvalid() > 0);
    }

    @Test
    void doesNotApplyBatchForbiddenCharacterRuleToRealtimeTerms() {
        var result = selector.select(new KeytermSelector.Sources(
                List.of("<special>"), List.of(), List.of(), List.of(), List.of()), KeytermSelector.realtimeDefaults());

        assertEquals(List.of("<special>"), result.terms());
    }
}
