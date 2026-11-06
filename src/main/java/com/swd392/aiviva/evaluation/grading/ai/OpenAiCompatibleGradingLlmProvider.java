package com.swd392.aiviva.evaluation.grading.ai;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OpenAiCompatibleGradingLlmProvider implements GradingLlmProvider {
    private final GradingAiProperties properties;
    private final RestClient client;
    private final RestClient probeClient;
    private volatile Instant lastProbeAt = Instant.EPOCH;
    private volatile boolean lastReady;
    private volatile String lastReason = "Not checked";

    public OpenAiCompatibleGradingLlmProvider(GradingAiProperties properties, RestClient client, RestClient probeClient) {
        this.properties = properties;
        this.client = client;
        this.probeClient = probeClient;
    }

    @Override public String providerName() { return "openai-compatible"; }
    @Override public String modelName() { return properties.getModel(); }
    @Override public boolean isReady() { probe(); return lastReady; }
    @Override public String reason() { probe(); return lastReady ? null : lastReason; }

    private synchronized void probe() {
        if (lastProbeAt.plusSeconds(5).isAfter(Instant.now())) return;
        lastProbeAt = Instant.now();
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            lastReady = false;
            lastReason = "GRADING_AI_MODEL is missing";
            return;
        }
        try {
            Map<?, ?> response = probeClient.get()
                    .uri(properties.getBaseUrl().replaceAll("/$", "") + "/models")
                    .headers(headers -> {
                        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) headers.setBearerAuth(properties.getApiKey());
                    })
                    .retrieve().body(Map.class);
            boolean modelFound = response != null && response.get("data") instanceof List<?> models
                    && models.stream().anyMatch(model -> model instanceof Map<?, ?> item
                    && properties.getModel().equals(String.valueOf(item.get("id"))));
            lastReady = modelFound;
            lastReason = modelFound ? "" : "Configured grading model was not returned by the local inference server";
        } catch (Exception exception) {
            lastReady = false;
            lastReason = "Local grading inference server is unavailable";
        }
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, double temperature) {
        Map<?, ?> response = client.post()
                .uri(properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                        headers.setBearerAuth(properties.getApiKey());
                    }
                })
                .body(Map.of("model", properties.getModel(), "temperature", temperature,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt))))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices)
                || choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)) {
            throw new IllegalStateException("Grading LLM returned an invalid response envelope");
        }
        return content;
    }
}
