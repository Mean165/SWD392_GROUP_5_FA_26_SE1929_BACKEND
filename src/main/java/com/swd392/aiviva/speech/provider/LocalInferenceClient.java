package com.swd392.aiviva.speech.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.tts.TTSStreamInfo;
import com.swd392.aiviva.speech.config.LocalInferenceProperties;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class LocalInferenceClient {

    private final LocalInferenceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LocalInferenceClient(LocalInferenceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs())))
                .build();
    }

    public boolean isConfigured() {
        return properties.getToken() != null && !properties.getToken().isBlank()
                && properties.getUrl() != null && !properties.getUrl().isBlank();
    }

    public JsonNode health() {
        requireConfigured();
        HttpRequest request = request("/health")
                .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .GET().build();
        return sendJson(request);
    }

    public JsonNode transcribe(byte[] audio, String filename, String language,
            List<String> hotwords, String initialPrompt) {
        requireConfigured();
        if (audio == null || audio.length == 0) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Audio is empty.");
        }
        try {
            String boundary = "aiviva-local-" + UUID.randomUUID();
            byte[] body = multipart(boundary, audio, filename, language, hotwords, initialPrompt);
            HttpRequest request = request("/v1/stt/transcribe")
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return sendJson(request);
        } catch (IOException exception) {
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_ERROR", "Could not prepare local transcription request.", exception);
        }
    }

    public byte[] synthesize(String text, String language, String voice, double speed, String format) {
        requireConfigured();
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "text", text,
                    "language", language,
                    "voice", voice,
                    "speed", speed,
                    "format", format,
                    "normalize", true));
            HttpRequest request = request("/v1/tts/synthesize")
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body() == null || response.body().length == 0) {
                throw new LocalInferenceException("LOCAL_TTS_FAILED", "Local TTS returned empty audio.", response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new LocalInferenceException("LOCAL_TTS_FAILED", "Could not encode local TTS request.", 502, exception);
        }
    }

    public TTSStreamInfo streamSynthesis(String text, String language, String voice, double speed,
            Consumer<byte[]> chunkConsumer) {
        requireConfigured();
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "text", text, "language", language, "voice", voice, "speed", speed,
                    "format", "pcm_s16le", "normalize", true));
            HttpRequest request = request("/v1/tts/stream")
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
            int sampleRate = parseHeader(response, "X-Sample-Rate", 22_050);
            int channels = parseHeader(response, "X-Channels", 1);
            long durationMs = parseHeader(response, "X-Duration-Ms", 0L);
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            try (InputStream input = response.body(); BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode event = objectMapper.readTree(line);
                    if ("audio".equals(event.path("type").asText())) {
                        String encoded = event.path("audio_base64").asText("");
                        if (!encoded.isBlank()) pcm.writeBytes(Base64.getDecoder().decode(encoded));
                    } else if ("error".equals(event.path("type").asText())) {
                        throw new LocalInferenceException("LOCAL_TTS_FAILED", "Local TTS stream failed.", 502);
                    } else if ("done".equals(event.path("type").asText())) {
                        durationMs = event.path("duration_ms").asLong(durationMs);
                    }
                }
            }
            if (pcm.size() == 0) throw new LocalInferenceException("LOCAL_TTS_FAILED", "Local TTS stream returned no PCM.", 502);
            byte[] wav = pcmToWav(pcm.toByteArray(), sampleRate, channels);
            int chunkBytes = 16 * 1024;
            for (int offset = 0; offset < wav.length; offset += chunkBytes) {
                chunkConsumer.accept(java.util.Arrays.copyOfRange(wav, offset, Math.min(wav.length, offset + chunkBytes)));
            }
            if (durationMs <= 0) durationMs = pcm.size() * 1000L / (sampleRate * channels * 2L);
            return new TTSStreamInfo("audio/wav", "wav", sampleRate, channels, durationMs);
        } catch (IOException exception) {
            throw new LocalInferenceException("LOCAL_TTS_FAILED", "Could not read local TTS stream.", 502, exception);
        }
    }

    public WebSocketConnection openSttWebSocket(String language, String sessionId, String turnId,
            java.net.http.WebSocket.Listener listener) {
        requireConfigured();
        String scheme = properties.getUrl().startsWith("https://") ? "wss" : "ws";
        String hostAndPath = properties.getUrl().replaceFirst("^https?://", "");
        String url = scheme + "://" + hostAndPath + "/v1/stt/stream?language=" + encode(language)
                + "&session_id=" + encode(sessionId) + "&turn_id=" + encode(turnId);
        try {
            var socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs())))
                    .header("X-Internal-Token", properties.getToken())
                    .buildAsync(URI.create(url), listener)
                    .get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
            return new WebSocketConnection(socket);
        } catch (Exception exception) {
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE",
                    "Local STT inference service could not open a WebSocket.", exception);
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(trimSlash(properties.getUrl()) + path))
                .header("X-Internal-Token", properties.getToken());
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            HttpResponse<T> response = httpClient.send(request, handler);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response.statusCode();
                String code = status == 401 ? "LOCAL_AUTH_FAILED"
                        : status == 429 || status == 503 ? "LOCAL_INFERENCE_BUSY"
                        : status == 413 ? "AUDIO_TOO_LARGE" : "LOCAL_INFERENCE_ERROR";
                String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
                int errorBytes = response.body() instanceof byte[] bytes ? bytes.length
                        : response.body() instanceof String value ? value.length() : -1;
                String reason = "response=" + contentType + ",bytes=" + errorBytes;
                JsonNode error = responseError(response.body());
                JsonNode detail = error.path("detail");
                if (detail.isObject()) {
                    if (detail.path("code").isTextual()) code = detail.path("code").asText();
                    String message = detail.path("message").asText("");
                    if (message.matches("[A-Za-z0-9_.-]{1,80}")) reason += ",reason=" + message;
                } else if (detail.isArray() && !detail.isEmpty()) {
                    JsonNode first = detail.get(0);
                    String field = first.path("loc").isArray() && !first.path("loc").isEmpty()
                            ? first.path("loc").get(first.path("loc").size() - 1).asText("") : "request";
                    String type = first.path("type").asText("invalid");
                    if (field.matches("[A-Za-z0-9_-]{1,40}") && type.matches("[A-Za-z0-9_-]{1,60}")) {
                        reason = field + ":" + type;
                    }
                    code = "LOCAL_REQUEST_VALIDATION_FAILED";
                }
                throw new LocalInferenceException(code,
                        "Local inference service returned HTTP " + status + " [" + code + "]"
                                + (reason.isBlank() ? "." : " (" + reason + ")."),
                        status, reason);
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_TIMEOUT", "Local inference request was interrupted.", exception);
        } catch (IOException exception) {
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local inference service is unavailable.", exception);
        }
    }

    private JsonNode sendJson(HttpRequest request) {
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_PROTOCOL_ERROR", "Local inference returned invalid JSON.", exception);
        }
    }

    private JsonNode responseError(Object body) {
        try {
            if (body instanceof byte[] bytes) return objectMapper.readTree(bytes);
            if (body instanceof String value) return objectMapper.readTree(value);
        } catch (IOException ignored) {
            // Error responses are intentionally reduced to status when they are not JSON.
        }
        return objectMapper.createObjectNode();
    }

    private byte[] multipart(String boundary, byte[] audio, String filename, String language,
            List<String> hotwords, String initialPrompt) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(audio.length + 2048);
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\""
                + safeFilename(filename) + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        body.write(audio);
        write(body, "\r\n");
        writeField(body, boundary, "language", language);
        writeField(body, boundary, "hotwords", objectMapper.writeValueAsString(hotwords == null ? List.of() : hotwords));
        writeField(body, boundary, "initial_prompt", initialPrompt == null ? "" : initialPrompt);
        writeField(body, boundary, "mode", "final");
        write(body, "--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n");
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String safeFilename(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}")) return "answer.wav";
        return value;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private int parseHeader(HttpResponse<?> response, String name, int fallback) {
        try { return Integer.parseInt(response.headers().firstValue(name).orElse(Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private long parseHeader(HttpResponse<?> response, String name, long fallback) {
        try { return Long.parseLong(response.headers().firstValue(name).orElse(Long.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private byte[] pcmToWav(byte[] pcm, int sampleRate, int channels) {
        ByteArrayOutputStream wav = new ByteArrayOutputStream(pcm.length + 44);
        writeAscii(wav, "RIFF"); writeLe32(wav, 36 + pcm.length); writeAscii(wav, "WAVEfmt ");
        writeLe32(wav, 16); writeLe16(wav, 1); writeLe16(wav, channels);
        writeLe32(wav, sampleRate); writeLe32(wav, sampleRate * channels * 2);
        writeLe16(wav, channels * 2); writeLe16(wav, 16); writeAscii(wav, "data");
        writeLe32(wav, pcm.length); wav.writeBytes(pcm);
        return wav.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeLe16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff); out.write((value >>> 8) & 0xff);
    }

    private void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff); out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff); out.write((value >>> 24) & 0xff);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new SpeechToTextProviderException("LOCAL_INFERENCE_NOT_CONFIGURED",
                    "Local inference URL and internal token must be configured.");
        }
    }

    public record WebSocketConnection(java.net.http.WebSocket socket) { }

    public static final class LocalInferenceException extends RuntimeException {
        private final String code;
        private final int status;
        private final String reason;

        public LocalInferenceException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
            this.reason = "";
        }

        public LocalInferenceException(String code, String message, int status, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.status = status;
            this.reason = "";
        }

        public LocalInferenceException(String code, String message, int status, String reason) {
            super(message);
            this.code = code;
            this.status = status;
            this.reason = reason;
        }

        public String code() { return code; }
        public int status() { return status; }
        public String reason() { return reason; }
    }
}
