package com.swd392.aiviva.interview.ai.tts;

import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiTtsProviderAdapter implements TextToSpeechProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsProviderAdapter.class);

    private final OpenAiConfig openAiConfig;
    private final RestClient restClient;

    public OpenAiTtsProviderAdapter(OpenAiConfig openAiConfig) {
        this.openAiConfig = openAiConfig;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(openAiConfig.getTimeout());
        requestFactory.setReadTimeout(openAiConfig.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public byte[] synthesize(String text) {
        return synthesize(text, openAiConfig.getLanguage());
    }

    @Override
    public byte[] synthesize(String text, String language) {
        return synthesize(text, language, openAiConfig.getResponseFormat());
    }

    @Override
    public byte[] synthesize(String text, String language, String responseFormat) {
        String effectiveLanguage = language == null || language.isBlank()
                ? openAiConfig.getLanguage() : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"vi".equals(effectiveLanguage) && !"en".equals(effectiveLanguage)) {
            throw new BusinessException("Interview language must be 'vi' or 'en'.");
        }
        return synthesize(text, effectiveLanguage, openAiConfig.getVoiceForLanguage(effectiveLanguage), openAiConfig.getSpeed(),
                responseFormat);
    }

    public byte[] synthesize(String text, String language, String voice, Double speed, String responseFormat) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("Question text is required for TTS synthesis.");
        }
        if (text.length() > openAiConfig.getMaxTextLength()) {
            throw new BusinessException("Question text exceeds the supported maximum length of " + openAiConfig.getMaxTextLength() + " characters.");
        }
        if (!openAiConfig.isConfigured()) {
            log.error("OpenAI TTS provider is not configured: API key is missing");
            throw new ExternalServiceException("OpenAI speech synthesis is not configured.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String effectiveVoice = (voice == null || voice.isBlank()) ? openAiConfig.getVoice() : voice;
        Double effectiveSpeed = (speed != null) ? speed : openAiConfig.getSpeed();
        String effectiveFormat = (responseFormat == null || responseFormat.isBlank()) ? openAiConfig.getResponseFormat() : responseFormat;
        if (!Double.isFinite(effectiveSpeed) || effectiveSpeed < 0.25 || effectiveSpeed > 4.0) {
            throw new BusinessException("TTS speed must be between 0.25 and 4.0.");
        }
        if (!isSupportedFormat(effectiveFormat)) {
            throw new BusinessException("Unsupported TTS response format.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", openAiConfig.getTtsModel(),
                "input", text,
                "voice", effectiveVoice,
                "speed", effectiveSpeed,
                "response_format", normalizeFormat(effectiveFormat)
        );

        try {
            byte[] audioBytes = restClient.post()
                    .uri(openAiConfig.getTtsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiConfig.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, response) -> {
                                log.warn("OpenAI TTS provider returned HTTP status {}",
                                        response.getStatusCode().value());
                                if (response.getStatusCode().value() == 429) {
                                    throw new ExternalServiceException(
                                            "OpenAI speech synthesis is rate limited; retry later.",
                                            HttpStatus.SERVICE_UNAVAILABLE);
                                }
                                throw new ExternalServiceException("OpenAI speech synthesis failed.");
                            })
                    .body(byte[].class);

            if (audioBytes == null || audioBytes.length == 0) {
                throw new ExternalServiceException("OpenAI returned an empty speech payload.");
            }

            return audioBytes;
        } catch (BusinessException | ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            log.error("OpenAI TTS request failed exceptionType={} rootCauseType={}",
                    e.getClass().getSimpleName(), rootCause.getClass().getSimpleName());
            throw new ExternalServiceException("Failed to synthesize speech via OpenAI.", e);
        }
    }

    private boolean isSupportedFormat(String format) {
        return format != null && switch (format.toLowerCase()) {
            case "mp3", "wav", "aac", "opus", "flac", "pcm" -> true;
            default -> false;
        };
    }

    private String normalizeFormat(String format) {
        return format.toLowerCase();
    }
}
