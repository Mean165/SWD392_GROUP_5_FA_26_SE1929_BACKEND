package com.swd392.aiviva.question.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiQuestionGenerationServiceImpl implements QuestionGenerationService {

    private final OpenAiConfig openAiConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiQuestionGenerationServiceImpl(OpenAiConfig openAiConfig, ObjectMapper objectMapper) {
        this.openAiConfig = openAiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String generateQuestionText(String prompt) {
        if (openAiConfig.getKey() == null || openAiConfig.getKey().isBlank()) {
            throw new BusinessException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", openAiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert viva examiner who creates rigorous, insightful, and challenging follow-up examination questions."),
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
                return choices.get(0).path("message").path("content").asText();
            }
            throw new BusinessException("Invalid response format received from OpenAI API");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Failed to generate question via OpenAI: " + e.getMessage());
        }
    }
}
