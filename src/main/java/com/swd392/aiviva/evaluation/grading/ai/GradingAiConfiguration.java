package com.swd392.aiviva.evaluation.grading.ai;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GradingAiProperties.class)
public class GradingAiConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GradingAiConfiguration.class);

    @Bean(name = "gradingLlmRestClient")
    RestClient gradingLlmRestClient(GradingAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(properties.getTimeoutSeconds() * 1000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean(name = "gradingLlmProbeClient")
    RestClient gradingLlmProbeClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(1500);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    GradingLlmProvider gradingLlmProvider(GradingAiProperties properties,
            @Qualifier("gradingLlmRestClient") RestClient client,
            @Qualifier("gradingLlmProbeClient") RestClient gradingLlmProbeClient, Environment env) {
        if (properties.isIncludeSignals()) {
            throw new IllegalStateException("INCLUDE_SIGNALS_IN_GRADING=true is not supported by this release; keep it false");
        }
        String provider = properties.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "disabled" -> new DisabledGradingLlmProvider();
            case "mock" -> {
                if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("test", "dev"))) {
                    throw new IllegalStateException("GRADING_AI_PROVIDER=mock is limited to test/dev profiles");
                }
                yield new MockGradingLlmProvider();
            }
            case "openai-compatible" -> {
                URI uri = URI.create(properties.getBaseUrl());
                boolean loopback = "localhost".equalsIgnoreCase(uri.getHost())
                        || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
                if (!loopback && !properties.isAllowRemote()) {
                    throw new IllegalStateException("Remote grading LLM is disabled; set GRADING_ALLOW_REMOTE_LLM=true to allow it");
                }
                if (!loopback) log.warn("Remote grading LLM explicitly enabled; grading transcripts may leave AiViva infrastructure");
                yield new OpenAiCompatibleGradingLlmProvider(properties, client, gradingLlmProbeClient);
            }
            default -> throw new IllegalStateException("Unsupported GRADING_AI_PROVIDER: " + properties.getProvider());
        };
    }
}
