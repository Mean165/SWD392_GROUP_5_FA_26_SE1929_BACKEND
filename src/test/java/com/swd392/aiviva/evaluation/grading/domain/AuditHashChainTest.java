package com.swd392.aiviva.evaluation.grading.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AuditHashChainTest {
    @Test
    void chainsEventsAndChangesWhenContentChanges() {
        String first = AuditHashChain.hash(null, "{\"action\":\"CREATED\"}");
        assertEquals(64, first.length());
        assertNotEquals(first, AuditHashChain.hash(first, "{\"action\":\"FINALIZED\"}"));
        assertNotEquals(first, AuditHashChain.hash(null, "{\"action\":\"FINALIZED\"}"));
    }
}
