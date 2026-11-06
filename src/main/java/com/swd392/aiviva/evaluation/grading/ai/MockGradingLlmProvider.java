package com.swd392.aiviva.evaluation.grading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockGradingLlmProvider implements GradingLlmProvider {
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public String providerName() { return "mock"; }
    @Override public String modelName() { return "deterministic-test-mock"; }
    @Override public boolean isReady() { return true; }
    @Override public String reason() { return null; }
    @Override public String complete(String systemPrompt, String userPrompt, double temperature) {
        try {
            JsonNode input = mapper.readTree(userPrompt);
            List<Map<String, Object>> criteria = new ArrayList<>();
            for (JsonNode criterion : input.path("criteria")) {
                criteria.add(Map.of("criterion", criterion.path("alias").asText(), "fraction", 0.5,
                        "justification", "Deterministic mock suggestion for development only.", "evidence", List.of()));
            }
            return mapper.writeValueAsString(Map.of("criteria", criteria, "strengths", List.of(),
                    "weaknesses", List.of(), "missingPoints", List.of(), "flags", List.of("MOCK_PROVIDER")));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Mock provider requires valid JSON input", exception);
        }
    }
}
