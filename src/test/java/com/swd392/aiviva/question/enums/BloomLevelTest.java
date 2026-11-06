package com.swd392.aiviva.question.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BloomLevelTest {
    @Test
    void exposesExactlySixLevelsInOrder() {
        assertEquals(6, BloomLevel.values().length);
        assertEquals(BloomLevel.REMEMBER, BloomLevel.parse(" remember "));
        assertEquals(BloomLevel.CREATE, BloomLevel.parse("create"));
        assertEquals(6, BloomLevel.CREATE.getOrder());
    }

    @Test
    void rejectsUnknownLevel() {
        assertThrows(IllegalArgumentException.class, () -> BloomLevel.parse("INVALID"));
    }
}
