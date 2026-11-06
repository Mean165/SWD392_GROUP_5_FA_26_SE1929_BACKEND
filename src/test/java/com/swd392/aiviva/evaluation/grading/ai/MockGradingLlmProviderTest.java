package com.swd392.aiviva.evaluation.grading.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MockGradingLlmProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsDevelopmentOnlySuggestionForEachCriterionAlias() throws Exception {
        MockGradingLlmProvider provider = new MockGradingLlmProvider();
        JsonNode input = mapper.readTree("""
                {"criteria":[{"alias":"C1"},{"alias":"C2"}]}
                """);
        JsonNode output = mapper.readTree(provider.complete("system", mapper.writeValueAsString(input), 0));
        assertEquals(2, output.path("criteria").size());
        assertEquals("C1", output.path("criteria").get(0).path("criterion").asText());
        assertEquals(0.5, output.path("criteria").get(0).path("fraction").asDouble());
        assertTrue(output.path("flags").toString().contains("MOCK_PROVIDER"));
    }
}
