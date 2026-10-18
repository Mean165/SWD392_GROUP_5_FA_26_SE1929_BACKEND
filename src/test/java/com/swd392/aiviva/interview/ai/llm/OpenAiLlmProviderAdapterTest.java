package com.swd392.aiviva.interview.ai.llm;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;

class OpenAiLlmProviderAdapterTest {

    @Test
    void failsExplicitlyWhenOpenAiIsNotConfiguredInsteadOfReturningSimulatedOutput() {
        OpenAiLlmProviderAdapter provider = new OpenAiLlmProviderAdapter(new OpenAiConfig(), new ObjectMapper());

        assertThrows(ExternalServiceException.class, () -> provider.generateText("Analyze an answer."));
    }
}
