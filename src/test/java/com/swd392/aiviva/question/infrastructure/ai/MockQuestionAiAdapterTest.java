package com.swd392.aiviva.question.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockQuestionAiAdapterTest {
    @Test
    void followsTheBloomCountsUsedByTheGenerationPrompt() throws Exception {
        MockQuestionAiAdapter adapter = new MockQuestionAiAdapter(new ObjectMapper());
        String prompt = "QUESTION_COUNT=4\nrequested Bloom counts: "
                + "{REMEMBER=1, UNDERSTAND=1, APPLY=1, ANALYZE=1}";

        var questions = new ObjectMapper().readTree(adapter.generateJson(prompt)).path("questions");
        Map<String, Integer> counts = new HashMap<>();
        questions.forEach(question -> counts.merge(question.path("bloomLevel").asText(), 1, Integer::sum));

        assertEquals(4, questions.size());
        assertEquals(Map.of("REMEMBER", 1, "UNDERSTAND", 1, "APPLY", 1, "ANALYZE", 1), counts);
    }
}
