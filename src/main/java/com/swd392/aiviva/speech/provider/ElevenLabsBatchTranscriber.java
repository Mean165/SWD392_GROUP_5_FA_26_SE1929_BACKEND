package com.swd392.aiviva.speech.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import com.swd392.aiviva.speech.stt.domain.STTWord;
import com.swd392.aiviva.speech.stt.provider.STTBatchTranscriber;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Batch finalizer using only multipart fields explicitly described in vendor docs. */
@Component
@ConditionalOnProperty(prefix = "speech.stt", name = "provider", havingValue = "elevenlabs")
public class ElevenLabsBatchTranscriber implements STTBatchTranscriber {

    private final SpeechToTextProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI apiBaseUri;

    @Autowired
    public ElevenLabsBatchTranscriber(SpeechToTextProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getElevenlabs().getConnectTimeoutSeconds()))
                .build(), URI.create("https://" + properties.getElevenlabs().realtimeHost()));
    }

    ElevenLabsBatchTranscriber(
            SpeechToTextProperties properties, ObjectMapper objectMapper, HttpClient httpClient, URI apiBaseUri) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBaseUri = apiBaseUri;
    }

    @Override
    public String providerName() {
        return "elevenlabs-scribe-v2-batch";
    }

    @Override
    public STTResult transcribeFull(UUID turnId, byte[] audio, String language, List<String> keyterms) {
        if (audio == null || audio.length == 0 || (audio.length & 1) != 0) {
            return failure(language, "INVALID_AUDIO_FORMAT", null);
        }
        if (properties.getElevenlabs().getApiKey() == null || properties.getElevenlabs().getApiKey().isBlank()) {
            return failure(language, "PROVIDER_UNAVAILABLE", null);
        }
        String languageCode;
        if ("vi-VN".equals(language)) languageCode = "vi";
        else if ("en-US".equals(language)) languageCode = "en";
        else return failure(language, "UNSUPPORTED_LANGUAGE", null);

        long startedAt = System.nanoTime();
        try {
            String boundary = "aiviva-stt-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, audio, languageCode);
            HttpRequest request = HttpRequest.newBuilder(apiBaseUri.resolve("/v1/speech-to-text"))
                    .timeout(Duration.ofSeconds(60))
                    .header("xi-api-key", properties.getElevenlabs().getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                String error = switch (httpResponse.statusCode()) {
                    case 401, 403 -> "PROVIDER_AUTH_ERROR";
                    case 413 -> "INPUT_TOO_LARGE";
                    case 429 -> "PROVIDER_RATE_LIMITED";
                    default -> httpResponse.statusCode() >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_PROTOCOL_ERROR";
                };
                return failure(language, error, elapsedMillis(startedAt));
            }
            JsonNode response = objectMapper.readTree(httpResponse.body());
            String text = response.path("text").asText("");
            String returnedLanguage = response.path("language_code").asText(languageCode);
            List<STTWord> words = parseWords(response.path("words"));
            Double confidence = words.stream().map(STTWord::confidence).filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
            Long durationMs = response.path("audio_duration").isNumber()
                    ? Math.round(response.path("audio_duration").asDouble() * 1000)
                    : audio.length * 1000L / 32_000L;
            String requestId = response.path("transcription_id").asText(null);
            return new STTResult(providerName(), "scribe_v2", "final", text, words, returnedLanguage,
                    confidence, confidence == null ? "none" : "word_logprob", durationMs,
                    elapsedMillis(startedAt), List.of(), requestId, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(language, "PROVIDER_TIMEOUT", elapsedMillis(startedAt));
        } catch (IOException exception) {
            return failure(language, "PROVIDER_PROTOCOL_ERROR", elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            return failure(language, "PROVIDER_UNAVAILABLE", elapsedMillis(startedAt));
        }
    }

    private byte[] multipartBody(String boundary, byte[] audio, String languageCode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 2048);
        writeFile(out, boundary, audio);
        writeField(out, boundary, "model_id", "scribe_v2");
        writeField(out, boundary, "file_format", "pcm_s16le_16");
        writeField(out, boundary, "language_code", languageCode);
        writeField(out, boundary, "timestamps_granularity", "word");
        writeField(out, boundary, "diarize", "false");
        writeField(out, boundary, "tag_audio_events", "false");
        writeField(out, boundary, "use_multi_channel", "false");
        writeField(out, boundary, "enable_logging", Boolean.toString(properties.getElevenlabs().isEnableLogging()));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(ByteArrayOutputStream out, String boundary, byte[] audio) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"answer.pcm\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(audio);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private List<STTWord> parseWords(JsonNode wordsNode) {
        if (!wordsNode.isArray()) return List.of();
        List<STTWord> words = new ArrayList<>();
        for (JsonNode word : wordsNode) {
            String text = word.path("text").asText("");
            if (text.isBlank()) continue;
            Long start = word.path("start").isNumber() ? Math.round(word.path("start").asDouble() * 1000) : null;
            Long end = word.path("end").isNumber() ? Math.round(word.path("end").asDouble() * 1000) : null;
            Double confidence = word.path("logprob").isNumber() ? Math.exp(word.path("logprob").asDouble()) : null;
            words.add(new STTWord(text, start, end, confidence, word.path("type").asText("word"),
                    word.path("speaker_id").asText(null)));
        }
        return List.copyOf(words);
    }

    private STTResult failure(String language, String error, Long latencyMs) {
        return new STTResult(providerName(), "scribe_v2", "final", null, List.of(), language,
                null, "none", null, latencyMs, List.of(), null, error);
    }
}
