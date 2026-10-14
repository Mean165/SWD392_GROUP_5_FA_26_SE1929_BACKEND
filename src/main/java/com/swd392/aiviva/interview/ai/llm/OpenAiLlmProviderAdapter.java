package com.swd392.aiviva.interview.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiLlmProviderAdapter implements LlmProviderAdapter {

    private final OpenAiConfig openAiConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiLlmProviderAdapter(OpenAiConfig openAiConfig, ObjectMapper objectMapper) {
        this.openAiConfig = openAiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String generateText(String prompt) {
        if (openAiConfig.getKey() == null || openAiConfig.getKey().isBlank()) {
            // Fallback for offline/mock development if API key is not configured
            return "Simulated AI Analysis for prompt: " + prompt;
        }

        Map<String, Object> requestBody = Map.of(
                "model", openAiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert AI Viva Interview Examiner. Provide clear, concise, and structured analytical responses."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            String response = restClient.post()
                    .uri(openAiConfig.getUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiConfig.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText().trim();
            }
            throw new BusinessException("Invalid response format received from OpenAI LLM");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Failed to call OpenAI LLM: " + e.getMessage());
        }
    }
}
