package com.swd392.aiviva.speech.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class ZeroWeightWebSocketClient implements ZeroWeightStreamingClient {

    private static final int HEADER_BYTES = 12;
    private static final int SAMPLE_RATE = 24_000;
    private final ObjectMapper objectMapper;
    private final ZeroWeightUsageMeter usageMeter;
    private final HttpClient httpClient;
    private final RestClient restClient;

    public ZeroWeightWebSocketClient(
            ObjectMapper objectMapper, ZeroWeightUsageMeter usageMeter, TextToSpeechProperties properties) {
        this.objectMapper = objectMapper;
        this.usageMeter = usageMeter;
        int timeoutMillis = properties.getZeroWeight().getTimeoutMillis();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ZeroWeightPcmResult streamPcm(
            String text,
            String contextId,
            ZeroWeightSessionOptions options,
            Consumer<byte[]> pcmFrameConsumer) {
        SessionInfo session = createSession(options);
        CompletableFuture<Void> contextDone = new CompletableFuture<>();
        StringBuilder textFrame = new StringBuilder();
        ByteArrayOutputStream binaryFrame = new ByteArrayOutputStream();
        AtomicReference<WebSocket> socketRef = new AtomicReference<>();
        AtomicReference<Throwable> frameError = new AtomicReference<>();
        AtomicLong socketOpenedAt = new AtomicLong();
        AtomicBoolean usageRecorded = new AtomicBoolean();
        var sequenceByContext = new HashMap<Integer, Long>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                socketRef.set(webSocket);
                socketOpenedAt.compareAndSet(0, System.nanoTime());
                webSocket.request(1);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onText(
                    WebSocket webSocket, CharSequence data, boolean last) {
                textFrame.append(data);
                if (last) {
                    handleServerText(textFrame.toString(), contextDone, frameError);
                    textFrame.setLength(0);
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onBinary(
                    WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] part = new byte[data.remaining()];
                data.get(part);
                binaryFrame.writeBytes(part);
                if (last) {
                    try {
                        byte[] payload = parseAudioFrame(binaryFrame.toByteArray(), sequenceByContext);
                        if (payload.length > 0) {
                            pcmFrameConsumer.accept(payload);
                        }
                    } catch (RuntimeException exception) {
                        frameError.compareAndSet(null, exception);
                        contextDone.completeExceptionally(exception);
                        webSocket.abort();
                    } finally {
                        binaryFrame.reset();
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onClose(
                    WebSocket webSocket, int statusCode, String reason) {
                if (!contextDone.isDone()) {
                    contextDone.completeExceptionally(closeFailure(statusCode));
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                contextDone.completeExceptionally(new ZeroWeightProviderException(
                        "TTS_NETWORK_ERROR", "ZeroWeight WebSocket connection failed.", error));
            }
        };

        try {
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(options.timeoutMillis()))
                    .buildAsync(URI.create(session.url()), listener)
                    .get(options.timeoutMillis(), TimeUnit.MILLISECONDS);
            socketRef.set(socket);
            socket.sendText(json(Map.of("type", "config", "context_id", contextId,
                    "speed", options.speed())), true)
                    .get(options.timeoutMillis(), TimeUnit.MILLISECONDS);
            socket.sendText(json(Map.of("type", "synthesize", "context_id", contextId,
                    "text", text, "segment_mode", "auto")), true)
                    .get(options.timeoutMillis(), TimeUnit.MILLISECONDS);
            socket.sendText(json(Map.of("type", "end", "context_id", contextId)), true)
                    .get(options.timeoutMillis(), TimeUnit.MILLISECONDS);

            contextDone.get(options.timeoutMillis(), TimeUnit.MILLISECONDS);
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "context complete")
                        .get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
            long socketMillis = elapsedMillis(socketOpenedAt.get());
            long credits = recordUsage(socketOpenedAt.get(), usageRecorded, session.gateway());
            return new ZeroWeightPcmResult(socketMillis, session.gateway(), credits);
        } catch (TimeoutException exception) {
            close(socketRef.get());
            recordUsage(socketOpenedAt.get(), usageRecorded, session.gateway());
            throw new ZeroWeightProviderException("TTS_TIMEOUT", "ZeroWeight TTS timed out.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close(socketRef.get());
            recordUsage(socketOpenedAt.get(), usageRecorded, session.gateway());
            throw new ZeroWeightProviderException("TTS_INTERRUPTED", "ZeroWeight TTS was interrupted.", exception);
        } catch (ExecutionException exception) {
            close(socketRef.get());
            recordUsage(socketOpenedAt.get(), usageRecorded, session.gateway());
            Throwable cause = exception.getCause();
            if (cause instanceof ZeroWeightProviderException providerException) {
                throw providerException;
            }
            throw new ZeroWeightProviderException("TTS_NETWORK_ERROR", "ZeroWeight TTS failed.", cause);
        }
    }

    private SessionInfo createSession(ZeroWeightSessionOptions options) {
        if (options.apiKey() == null || options.apiKey().isBlank()) {
            throw new ZeroWeightProviderException("TTS_NOT_CONFIGURED", "ZeroWeight API key is not configured.");
        }
        Map<String, Object> sampling = Map.of(
                "temperature", 0.8,
                "repetition_penalty", 1.2,
                "topk", 25,
                "topp", 0.95);
        Map<String, Object> request = Map.of(
                "voice_id", options.voiceId(),
                "format", "pcm_s16le",
                "sample_rate", SAMPLE_RATE,
                "normalize_text", options.normalizeText(),
                "sampling", sampling);
        try {
            JsonNode response = restClient.post()
                    .uri(options.baseUrl() + "/api/v1/realtime/stream/session")
                    .header("X-ZW-Api-Key", options.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String url = response == null ? null : response.path("url").asText(null);
            if (!isAllowedSocketUrl(url)) {
                throw new ZeroWeightProviderException("TTS_BAD_SESSION_RESPONSE", "ZeroWeight returned an invalid WebSocket URL.");
            }
            return new SessionInfo(url, response.path("gateway").asText("unknown"));
        } catch (HttpStatusCodeException exception) {
            throw httpFailure(exception.getStatusCode().value());
        } catch (ZeroWeightProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ZeroWeightProviderException("TTS_SESSION_ERROR", "Could not create a ZeroWeight session.", exception);
        }
    }

    static byte[] parseAudioFrame(byte[] frame, Map<Integer, Long> sequenceByContext) {
        if (frame.length < HEADER_BYTES) {
            throw new ZeroWeightProviderException("TTS_BAD_AUDIO_FRAME", "ZeroWeight returned a truncated audio frame.");
        }
        ByteBuffer header = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        int version = Byte.toUnsignedInt(header.get());
        header.get(); // flags
        int contextOrdinal = Short.toUnsignedInt(header.getShort());
        long sequence = Integer.toUnsignedLong(header.getInt());
        Integer.toUnsignedLong(header.getInt()); // presentation timestamp
        if (version != 1) {
            throw new ZeroWeightProviderException("TTS_BAD_AUDIO_FRAME", "Unsupported ZeroWeight audio frame version.");
        }
        Long expected = sequenceByContext.get(contextOrdinal);
        if (expected != null && sequence != expected) {
            throw new ZeroWeightProviderException("TTS_BAD_AUDIO_FRAME", "ZeroWeight audio frame sequence is discontinuous.");
        }
        sequenceByContext.put(contextOrdinal, sequence + 1);
        int payloadLength = frame.length - HEADER_BYTES;
        if ((payloadLength & 1) != 0) {
            throw new ZeroWeightProviderException("TTS_BAD_AUDIO_FRAME", "ZeroWeight returned an incomplete PCM16 sample.");
        }
        return java.util.Arrays.copyOfRange(frame, HEADER_BYTES, frame.length);
    }

    private void handleServerText(
            String json, CompletableFuture<Void> contextDone, AtomicReference<Throwable> frameError) {
        try {
            JsonNode message = objectMapper.readTree(json);
            String type = message.path("type").asText("");
            if ("context_done".equals(type)) {
                contextDone.complete(null);
            } else if ("error".equals(type)) {
                String messageText = message.path("message").asText("").toLowerCase(java.util.Locale.ROOT);
                String code = messageText.contains("credit") ? "TTS_CREDITS_EXHAUSTED" : "TTS_STREAM_ERROR";
                ZeroWeightProviderException exception = new ZeroWeightProviderException(code,
                        "ZeroWeight synthesis failed.");
                frameError.compareAndSet(null, exception);
                contextDone.completeExceptionally(exception);
            }
        } catch (Exception exception) {
            ZeroWeightProviderException failure = new ZeroWeightProviderException(
                    "TTS_PROTOCOL_ERROR", "Invalid ZeroWeight WebSocket event.", exception);
            frameError.compareAndSet(null, failure);
            contextDone.completeExceptionally(failure);
        }
    }

    private ZeroWeightProviderException httpFailure(int status) {
        String code = switch (status) {
            case 401 -> "TTS_AUTH_FAILED";
            case 402 -> "TTS_CREDITS_EXHAUSTED";
            case 422 -> "TTS_REQUEST_REJECTED";
            case 429 -> "TTS_RATE_LIMITED";
            case 503 -> "TTS_PROVIDER_UNAVAILABLE";
            default -> "TTS_SESSION_ERROR";
        };
        return new ZeroWeightProviderException(code, "ZeroWeight session request failed with HTTP " + status + ".");
    }

    private boolean isAllowedSocketUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            if ("wss".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            String host = uri.getHost();
            return "ws".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ZeroWeightProviderException closeFailure(int statusCode) {
        String code = switch (statusCode) {
            case 4001 -> "TTS_TOKEN_EXPIRED";
            case 4002 -> "TTS_CONCURRENCY_LIMIT";
            case 4003 -> "TTS_REQUEST_REJECTED";
            case 4004 -> "TTS_CREDITS_EXHAUSTED";
            case 4029 -> "TTS_RATE_LIMITED";
            case 1013 -> "TTS_PROVIDER_CAPACITY";
            case 1011 -> "TTS_PROVIDER_ERROR";
            case 1008 -> "TTS_BACKPRESSURE";
            default -> "TTS_SOCKET_CLOSED";
        };
        return new ZeroWeightProviderException(code, "ZeroWeight WebSocket closed with code " + statusCode + ".");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ZeroWeightProviderException("TTS_PROTOCOL_ERROR", "Could not encode ZeroWeight request.", exception);
        }
    }

    private void close(WebSocket socket) {
        if (socket != null && !socket.isOutputClosed()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "server error");
        }
    }

    private long elapsedMillis(long startedAt) {
        return startedAt == 0 ? 0 : Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private long recordUsage(long openedAt, AtomicBoolean recorded, String gateway) {
        if (openedAt == 0 || !recorded.compareAndSet(false, true)) {
            return 0;
        }
        return usageMeter.recordSession(elapsedMillis(openedAt), gateway);
    }

    private record SessionInfo(String url, String gateway) {
    }
}
