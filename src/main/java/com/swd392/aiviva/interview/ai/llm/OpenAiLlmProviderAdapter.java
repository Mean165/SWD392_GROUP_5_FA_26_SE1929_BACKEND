package com.swd392.aiviva.interview.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(openAiConfig.getTimeout());
        requestFactory.setReadTimeout(openAiConfig.getTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String generateText(String prompt) {
        if (openAiConfig.getKey() == null || openAiConfig.getKey().isBlank()) {
            throw new ExternalServiceException("OpenAI is not configured. Set OPENAI_API_KEY to use interview AI.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        Map<String, Object> requestBody = Map.of(
                "model", openAiConfig.getModel(),
                "response_format", Map.of("type", "json_object"),
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
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, providerResponse) -> {
                                if (providerResponse.getStatusCode().value() == 429) {
                                    throw new ExternalServiceException(
                                            "OpenAI answer analysis is rate limited; retry later.",
                                            HttpStatus.SERVICE_UNAVAILABLE);
                                }
                                throw new ExternalServiceException("OpenAI answer analysis failed.");
                            })
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new ExternalServiceException("OpenAI returned an empty answer-analysis response.");
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                if (content != null && !content.isBlank()) {
                    return content.trim();
                }
            }
            throw new ExternalServiceException("OpenAI returned an invalid answer-analysis response.");
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Failed to call the OpenAI answer-analysis service.", e);
        }
    }
}
