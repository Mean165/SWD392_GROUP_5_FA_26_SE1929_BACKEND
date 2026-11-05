package com.swd392.aiviva.evaluation.grading.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleGradingLlmProviderTest {
    @Test
    void probesModelsAndParsesStructuredChatResponse() {
        GradingAiProperties properties = new GradingAiProperties();
        properties.setBaseUrl("http://127.0.0.1:11434/v1");
        properties.setModel("local-grader");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:11434/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"local-grader\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:11434/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"{\\\"criteria\\\":[]}\"}}]}", MediaType.APPLICATION_JSON));

        RestClient client = builder.build();
        OpenAiCompatibleGradingLlmProvider provider = new OpenAiCompatibleGradingLlmProvider(properties, client, client);
        assertTrue(provider.isReady());
        assertEquals("{\"criteria\":[]}", provider.complete("system", "user", 0));
        server.verify();
    }
}
