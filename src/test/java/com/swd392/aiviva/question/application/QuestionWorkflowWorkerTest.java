package com.swd392.aiviva.question.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.question.infrastructure.ai.MockQuestionAiAdapter;
import com.swd392.aiviva.question.dto.QuestionWorkflowDtos.GenerateRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionWorkflowWorkerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mockAdapterProducesQuestionsMatchingBloomAllocationAndSchema() throws Exception {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("REMEMBER", 1);
        distribution.put("UNDERSTAND", 1);
        distribution.put("APPLY", 0);
        distribution.put("ANALYZE", 0);
        GenerateRequest request = request(distribution, 2);
        MockQuestionAiAdapter mock = new MockQuestionAiAdapter(mapper);
        String json = mock.generateJson("QUESTION_COUNT=2\nBloom distribution: " + distribution + ".");

        JsonNode result = QuestionWorkflowWorker.validateResponse(mapper.readTree(json), request, 1);

        assertEquals(2, result.size());
        assertEquals("REMEMBER", result.get(0).get("bloomLevel").asText());
        assertEquals("UNDERSTAND", result.get(1).get("bloomLevel").asText());
    }

    @Test
    void rejectsRubricWithWrongTotalAndOutOfRangeCitation() throws Exception {
        GenerateRequest request = request(Map.of("UNDERSTAND", 1), 1);
        MockQuestionAiAdapter mock = new MockQuestionAiAdapter(mapper);
        JsonNode valid = mapper.readTree(mock.generateJson("QUESTION_COUNT=1\nBloom distribution: {UNDERSTAND=1}."));
        ((com.fasterxml.jackson.databind.node.ObjectNode) valid.path("questions").get(0).path("rubric").get(0)).put("maxPoints", 8);
        assertThrows(IllegalArgumentException.class, () -> QuestionWorkflowWorker.validateResponse(valid, request, 1));

        JsonNode invalidCitation = mapper.readTree(mock.generateJson("QUESTION_COUNT=1\nBloom distribution: {UNDERSTAND=1}."));
        ((com.fasterxml.jackson.databind.node.ArrayNode) invalidCitation.path("questions").get(0).path("citationIndexes")).set(0, mapper.getNodeFactory().numberNode(3));
        assertThrows(IllegalArgumentException.class, () -> QuestionWorkflowWorker.validateResponse(invalidCitation, request, 1));
    }

    private GenerateRequest request(Map<String, Integer> bloom, int count) {
        return new GenerateRequest(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), count, bloom, "en", true, new BigDecimal("10"));
    }
}
