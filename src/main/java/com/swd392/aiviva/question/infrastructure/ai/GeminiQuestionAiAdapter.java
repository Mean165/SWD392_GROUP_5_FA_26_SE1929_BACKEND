package com.swd392.aiviva.question.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.question.ai.port.EmbeddingPort;
import com.swd392.aiviva.question.ai.port.LlmPort;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "question.ai.provider", havingValue = "gemini")
public class GeminiQuestionAiAdapter implements LlmPort, EmbeddingPort {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String embeddingModel;

    public GeminiQuestionAiAdapter(ObjectMapper mapper,
            @Value("${question.ai.api-key:}") String apiKey,
            @Value("${question.ai.model:gemini-3.8-flash}") String model,
            @Value("${question.ai.embedding-model:gemini-embedding-001}") String embeddingModel,
            @Value("${question.ai.timeout-seconds:45}") long timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.embeddingModel = embeddingModel;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().requestFactory(factory).baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
    }

    @Override
    public String generateJson(String prompt) {
        JsonNode response = call("/models/" + model + ":generateContent", Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2)));
        String text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        if (text.isBlank()) throw new IllegalStateException("Gemini returned an empty generation response");
        return text;
    }

    @Override
    public List<Double> embed(String text) {
        JsonNode response = call("/models/" + embeddingModel + ":embedContent", Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text)))));
        JsonNode values = response.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("Gemini returned an empty embedding");
        java.util.ArrayList<Double> vector = new java.util.ArrayList<>();
        values.forEach(value -> vector.add(value.asDouble()));
        return vector;
    }

    private JsonNode call(String path, Object body) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("GEMINI_API_KEY is not configured");
        try {
            String json = client.post().uri(path).header("x-goog-api-key", apiKey).body(body)
                    .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException("Gemini request failed with HTTP " + response.getStatusCode().value());
                    }).body(String.class);
            return mapper.readTree(json);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Gemini provider request failed", e);
        }
    }
}
