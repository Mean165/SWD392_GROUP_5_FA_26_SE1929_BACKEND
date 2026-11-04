package com.swd392.aiviva.question.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.question.ai.port.EmbeddingPort;
import com.swd392.aiviva.question.ai.port.LlmPort;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
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
    private static final int MAX_429_RETRIES = 2;
    private static final long MAX_RETRY_DELAY_SECONDS = 120;

    public GeminiQuestionAiAdapter(ObjectMapper mapper,
            @Value("${question.ai.api-key:}") String apiKey,
            @Value("${question.ai.model:gemini-3.8-flash}") String model,
            @Value("${question.ai.embedding-model:gemini-embedding-001}") String embeddingModel,
            @Value("${question.ai.timeout-seconds:45}") long timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("GEMINI_API_KEY is required when QUESTION_AI_PROVIDER=gemini");
        if (timeoutSeconds < 1) throw new IllegalArgumentException("LLM_TIMEOUT_SECONDS must be at least 1 second");
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
        return embed(text, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public List<Double> embedDocument(String text) {
        return embed(text, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public List<Double> embedQuery(String text) {
        return embed(text, "RETRIEVAL_QUERY");
    }

    private List<Double> embed(String text, String taskType) {
        JsonNode response = call("/models/" + embeddingModel + ":embedContent", Map.of(
                "model", "models/" + embeddingModel,
                "taskType", taskType,
                "content", Map.of("parts", List.of(Map.of("text", text)))));
        JsonNode values = response.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("Gemini returned an empty embedding");
        java.util.ArrayList<Double> vector = new java.util.ArrayList<>();
        values.forEach(value -> vector.add(value.asDouble()));
        return vector;
    }

    private JsonNode call(String path, Object body) {
        for (int attempt = 0; ; attempt++) {
            try {
                String json = client.post().uri(path).header("x-goog-api-key", apiKey).body(body)
                        .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            long retryAfter = retryAfterSeconds(response.getHeaders(), responseBody);
                            throw new GeminiApiException(response.getStatusCode().value(), retryAfter, responseBody);
                        }).body(String.class);
                return mapper.readTree(json);
            } catch (GeminiApiException e) {
                if (!e.retryable() || attempt >= MAX_429_RETRIES) throw e;
                long delay = e.retryAfterSeconds() > 0 ? e.retryAfterSeconds() : attempt + 1L;
                if (delay > MAX_RETRY_DELAY_SECONDS) throw e;
                try {
                    Thread.sleep(delay * 1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Gemini retry was interrupted", interrupted);
                }
            } catch (Exception e) {
                if (e instanceof IllegalStateException state) throw state;
                throw new IllegalStateException("Gemini provider request failed", e);
            }
        }
    }

    private long retryAfterSeconds(HttpHeaders headers, String body) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try { return Math.max(0, Long.parseLong(retryAfter.trim())); }
            catch (NumberFormatException ignored) {
                try { return Math.max(0, ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().getEpochSecond() - Instant.now().getEpochSecond()); }
                catch (Exception ignoredDate) { }
            }
        }
        try {
            JsonNode root = mapper.readTree(body);
            for (JsonNode detail : root.path("error").path("details")) {
                String delay = detail.path("retryDelay").asText("");
                if (delay.endsWith("s")) return Math.max(0, (long) Math.ceil(Double.parseDouble(delay.substring(0, delay.length() - 1))));
            }
        } catch (Exception ignored) { }
        return 0;
    }
}
