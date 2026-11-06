package com.swd392.aiviva.interview.ai.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiWhisperSttProviderAdapter implements SpeechToTextProviderAdapter {

    private final OpenAiConfig openAiConfig;
    private final InterviewConfig interviewConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiWhisperSttProviderAdapter(
            OpenAiConfig openAiConfig, InterviewConfig interviewConfig, ObjectMapper objectMapper) {
        this.openAiConfig = openAiConfig;
        this.interviewConfig = interviewConfig;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(openAiConfig.getTimeout());
        requestFactory.setReadTimeout(openAiConfig.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String transcribe(String audioDataUrl) {
        return transcribe(audioDataUrl, openAiConfig.getLanguage());
    }

    @Override
    public String transcribe(String audioDataUrl, String language) {
        if (audioDataUrl == null || audioDataUrl.isBlank()) {
            throw new BusinessException("Audio is required for speech transcription.");
        }
        AudioPayload payload = decodeAudioDataUrl(audioDataUrl);
        return transcribe(payload.bytes(), payload.filename(), language, false);
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename, String language) {
        return transcribe(audioBytes, filename, language, true);
    }

    private String transcribe(byte[] audioBytes, String filename, String language, boolean allowEmptyTranscript) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException("Audio is required for speech transcription.");
        }
        if (audioBytes.length > interviewConfig.getMaxAudioBytes()) {
            throw new BusinessException("Audio payload exceeds the maximum size.");
        }
        if (filename == null || filename.isBlank()) {
            throw new BusinessException("Audio filename is required for speech transcription.");
        }
        if (!openAiConfig.isConfigured()) {
            throw new ExternalServiceException("OpenAI speech transcription is not configured.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            form.add("model", openAiConfig.getSttModel());
            form.add("language", normalizeLanguage(language));

            String response = restClient.post()
                    .uri(openAiConfig.getSttUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiConfig.getKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, responseEntity) -> {
                                if (responseEntity.getStatusCode().value() == 429) {
                                    throw new ExternalServiceException(
                                            "OpenAI speech transcription is rate limited; retry later.",
                                            HttpStatus.SERVICE_UNAVAILABLE);
                                }
                                throw new ExternalServiceException("OpenAI speech transcription failed.");
                            })
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new ExternalServiceException("OpenAI returned an empty transcription response.");
            }

            JsonNode root = objectMapper.readTree(response);
            String transcript = root.path("text").asText(null);
            if (transcript == null || (transcript.isBlank() && !allowEmptyTranscript)) {
                throw new ExternalServiceException("OpenAI returned no transcript text.");
            }
            return transcript == null ? "" : transcript.trim();
        } catch (BusinessException | ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Failed to transcribe audio via OpenAI.", e);
        }
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null || language.isBlank()
                ? openAiConfig.getLanguage() : language.trim().toLowerCase(Locale.ROOT);
        if (!"vi".equals(normalized) && !"en".equals(normalized)) {
            throw new BusinessException("Interview language must be 'vi' or 'en'.");
        }
        return normalized;
    }

    private AudioPayload decodeAudioDataUrl(String dataUrl) {
        if (!dataUrl.startsWith("data:")) {
            throw new BusinessException("Audio must be provided as a base64 data URL; remote URLs are not accepted.");
        }

        int comma = dataUrl.indexOf(',');
        int metadataEnd = comma < 0 ? dataUrl.length() : comma;
        String metadata = dataUrl.substring(5, metadataEnd);
        String[] parts = metadata.split(";");
        String contentType = parts.length > 0 ? parts[0].toLowerCase() : "";
        if (!List.of("audio/webm", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4",
                "audio/m4a", "audio/ogg", "audio/aac", "audio/flac").contains(contentType)
                || parts.length < 2 || !"base64".equalsIgnoreCase(parts[parts.length - 1])) {
            throw new BusinessException("Unsupported audio format. Provide base64 audio/webm, audio/wav, audio/mpeg, audio/mp4, audio/ogg, audio/aac, or audio/flac.");
        }

        if (comma < 0 || comma == dataUrl.length() - 1) {
            throw new BusinessException("Audio payload is empty.");
        }
        String encoded = dataUrl.substring(comma + 1);
        if (encoded.length() > ((long) interviewConfig.getMaxAudioBytes() * 4 / 3) + 8) {
            throw new BusinessException("Audio payload exceeds the maximum size.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Audio payload is not valid base64.");
        }
        if (bytes.length == 0) {
            throw new BusinessException("Audio payload is empty.");
        }
        if (bytes.length > interviewConfig.getMaxAudioBytes()) {
            throw new BusinessException("Audio payload exceeds the maximum size.");
        }
        String extension = switch (contentType) {
            case "audio/webm" -> "webm";
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4" -> "mp4";
            case "audio/m4a" -> "m4a";
            case "audio/ogg" -> "ogg";
            case "audio/aac" -> "aac";
            case "audio/flac" -> "flac";
            default -> throw new BusinessException("Unsupported audio format.");
        };
        return new AudioPayload(bytes, "answer." + extension);
    }

    private record AudioPayload(byte[] bytes, String filename) {
    }
}
