package com.swd392.aiviva.speech.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** ElevenLabs realtime adapter using manual endpointing and server-side API-key auth. */
@Component
@ConditionalOnProperty(prefix = "speech.stt", name = "provider", havingValue = "elevenlabs")
public class ElevenLabsSpeechToTextProvider implements SpeechToTextProvider {

    private static final int SAMPLE_RATE = 16_000;
    private static final int MAX_KEYTERMS = 50;
    private static final int MAX_KEYTERM_CODEPOINTS = 20;

    private final SpeechToTextProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ElevenLabsSpeechToTextProvider(SpeechToTextProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getElevenlabs().getConnectTimeoutSeconds()))
                .build());
    }

    ElevenLabsSpeechToTextProvider(
            SpeechToTextProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isAvailable() {
        if (blank(properties.getElevenlabs().getApiKey())) return false;
        try {
            properties.getElevenlabs().realtimeHost();
            return properties.getElevenlabs().getConnectTimeoutSeconds() > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String readinessErrorCode() {
        return blank(properties.getElevenlabs().getApiKey()) ? "PROVIDER_NOT_CONFIGURED" : "STT_NOT_READY";
    }

    @Override
    public String providerName() {
        return "elevenlabs-scribe-v2-realtime (scribe_v2_realtime)";
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, SpeechToTextResultListener listener) {
        return openStreamingSession(language, sampleRate, List.of(), listener);
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, List<String> keyterms, SpeechToTextResultListener listener) {
        requireConfiguration();
        if (sampleRate != SAMPLE_RATE) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "ElevenLabs STT expects mono PCM16 at 16000 Hz.");
        }
        String languageCode = switch (language == null ? "" : language) {
            case "vi-VN" -> "vi";
            case "en-US" -> "en";
            default -> throw new SpeechToTextProviderException("UNSUPPORTED_LANGUAGE", "Language must be vi-VN or en-US.");
        };
        List<String> terms = validateKeyterms(keyterms);
        URI uri = realtimeUri(languageCode, terms);
        ProviderSession session = new ProviderSession(listener);
        try {
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getElevenlabs().getConnectTimeoutSeconds()))
                    .header("xi-api-key", properties.getElevenlabs().getApiKey())
                    .buildAsync(uri, session)
                    .get(properties.getElevenlabs().getConnectTimeoutSeconds(), TimeUnit.SECONDS);
            session.attach(socket);
            session.awaitOpen(Duration.ofSeconds(properties.getElevenlabs().getConnectTimeoutSeconds()));
            return session;
        } catch (TimeoutException exception) {
            session.abort();
            throw new SpeechToTextProviderException("PROVIDER_TIMEOUT", "ElevenLabs realtime connection timed out.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            session.abort();
            throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs realtime connection was interrupted.", exception);
        } catch (ExecutionException exception) {
            session.abort();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw handshakeFailure(cause);
        } catch (RuntimeException exception) {
            session.abort();
            throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs realtime provider could not open a stream.", exception);
        }
    }

    private SpeechToTextProviderException handshakeFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof WebSocketHandshakeException handshake) {
            int status = handshake.getResponse().statusCode();
            String code = switch (status) {
                case 401, 403 -> "PROVIDER_AUTH_ERROR";
                case 402 -> "PROVIDER_QUOTA_EXCEEDED";
                case 429 -> "PROVIDER_RATE_LIMITED";
                default -> status >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_PROTOCOL_ERROR";
            };
            return new SpeechToTextProviderException(code,
                    "ElevenLabs rejected the realtime WebSocket handshake (HTTP " + status + ").", cause);
        }
        return new SpeechToTextProviderException("PROVIDER_UNAVAILABLE",
                "ElevenLabs realtime provider could not open a stream.", failure);
    }

    private URI realtimeUri(String language, List<String> keyterms) {
        StringBuilder query = new StringBuilder("model_id=scribe_v2_realtime")
                .append("&audio_format=pcm_16000")
                .append("&language_code=").append(encode(language))
                .append("&commit_strategy=manual")
                .append("&include_timestamps=true")
                .append("&include_language_detection=true")
                .append("&keepalive_interval_ms=5000")
                .append("&enable_logging=").append(properties.getElevenlabs().isEnableLogging());
        for (String term : keyterms) query.append("&keyterms=").append(encode(term));
        return URI.create("wss://" + properties.getElevenlabs().realtimeHost()
                + "/v1/speech-to-text/realtime?" + query);
    }

    private List<String> validateKeyterms(List<String> input) {
        if (input == null || input.isEmpty()) return List.of();
        if (input.size() > MAX_KEYTERMS) {
            throw new SpeechToTextProviderException("INVALID_KEYTERMS", "Realtime STT accepts at most 50 keyterms.");
        }
        List<String> result = new ArrayList<>(input.size());
        for (String term : input) {
            if (term == null || term.isBlank() || term.codePointCount(0, term.length()) > MAX_KEYTERM_CODEPOINTS) {
                throw new SpeechToTextProviderException("INVALID_KEYTERMS", "Realtime keyterms must contain 1 to 20 characters.");
            }
            result.add(term);
        }
        return List.copyOf(result);
    }

    private void requireConfiguration() {
        if (blank(properties.getElevenlabs().getApiKey())) {
            throw new SpeechToTextProviderException("PROVIDER_NOT_CONFIGURED", "ElevenLabs STT API key is not configured.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private final class ProviderSession implements SpeechToTextStreamingSession, WebSocket.Listener {
        private final SpeechToTextResultListener listener;
        private final CompletableFuture<Void> opened = new CompletableFuture<>();
        private final CompletableFuture<Void> terminal = new CompletableFuture<>();
        private final CompletableFuture<Void> finalReceived = new CompletableFuture<>();
        private final StringBuilder incomingText = new StringBuilder();
        private final StringBuilder finalTranscript = new StringBuilder();
        private volatile WebSocket socket;
        private byte[] pendingFrame;
        private boolean closed;

        private ProviderSession(SpeechToTextResultListener listener) {
            this.listener = listener;
        }

        private void attach(WebSocket socket) {
            this.socket = socket;
        }

        private void awaitOpen(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
            opened.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            opened.complete(null);
            webSocket.request(1);
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16MonoFrame) {
            if (closed || socket == null) throw unavailable("ElevenLabs realtime stream is closed.");
            if (pcm16MonoFrame == null || pcm16MonoFrame.length == 0 || (pcm16MonoFrame.length & 1) != 0) {
                throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "PCM16 frames must contain complete samples.");
            }
            if (pendingFrame != null) sendChunk(pendingFrame, false);
            pendingFrame = pcm16MonoFrame.clone();
        }

        @Override
        public String finish(Duration timeout) {
            synchronized (this) {
                if (closed) return finalTranscript.toString();
                closed = true;
                if (pendingFrame == null) {
                    closeSocket();
                    return "";
                }
                sendChunk(pendingFrame, true);
                pendingFrame = null;
            }
            try {
                CompletableFuture.anyOf(finalReceived, terminal).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (terminal.isCompletedExceptionally() && !finalReceived.isDone()) terminal.join();
                return finalTranscript.toString();
            } catch (TimeoutException exception) {
                abort();
                throw new SpeechToTextProviderException("PROVIDER_TIMEOUT", "ElevenLabs did not return a committed transcript before timeout.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                abort();
                throw new SpeechToTextProviderException("PROVIDER_TIMEOUT", "Waiting for the ElevenLabs transcript was interrupted.", exception);
            } catch (ExecutionException exception) {
                abort();
                Throwable cause = exception.getCause();
                if (cause instanceof SpeechToTextProviderException providerException) throw providerException;
                throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs realtime stream failed.", cause);
            } catch (RuntimeException exception) {
                abort();
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof SpeechToTextProviderException providerException) throw providerException;
                throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs realtime stream failed.", cause);
            } finally {
                closeSocket();
            }
        }

        private void sendChunk(byte[] bytes, boolean commit) {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("message_type", "input_audio_chunk");
            message.put("audio_base_64", java.util.Base64.getEncoder().encodeToString(bytes));
            message.put("commit", commit);
            message.put("sample_rate", SAMPLE_RATE);
            socket.sendText(message.toString(), true).whenComplete((ignored, error) -> {
                if (error != null) fail(new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs stopped accepting audio.", error));
            });
        }

        @Override
        public synchronized void abort() {
            if (closed && socket == null) return;
            closed = true;
            pendingFrame = null;
            closeSocket();
            terminal.complete(null);
        }

        private void closeSocket() {
            WebSocket current = socket;
            socket = null;
            if (current != null && !current.isOutputClosed()) {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "turn complete");
            }
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (incomingText) {
                incomingText.append(data);
                if (last) {
                    String message = incomingText.toString();
                    incomingText.setLength(0);
                    handleMessage(message);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            fail(new SpeechToTextProviderException("PROVIDER_PROTOCOL_ERROR", "Unexpected binary frame from ElevenLabs STT."));
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private void handleMessage(String payload) {
            try {
                JsonNode message = objectMapper.readTree(payload);
                String type = message.path("message_type").asText();
                switch (type) {
                    case "session_started", "warning" -> { }
                    case "partial_transcript" -> listener.onPartial(message.path("text").asText(""));
                    case "committed_transcript", "committed_transcript_with_timestamps" -> {
                        String text = message.path("text").asText("").trim();
                        if (!text.isEmpty()) {
                            synchronized (finalTranscript) {
                                if (!finalTranscript.isEmpty()) finalTranscript.append(' ');
                                finalTranscript.append(text);
                            }
                            listener.onFinal(text, message.path("words").isArray()
                                    ? confidenceFromWords(message.path("words")) : 0.0);
                        }
                        finalReceived.complete(null);
                    }
                    case "auth_error" -> fail(providerError("PROVIDER_AUTH_ERROR", message));
                    case "quota_exceeded", "resource_exhausted" -> fail(providerError("PROVIDER_QUOTA_EXCEEDED", message));
                    case "commit_throttled", "rate_limited", "queue_overflow" -> fail(providerError("PROVIDER_RATE_LIMITED", message));
                    case "unaccepted_terms" -> fail(providerError("PROVIDER_TERMS_NOT_ACCEPTED", message));
                    case "session_time_limit_exceeded" -> fail(providerError("SESSION_TIMEOUT", message));
                    case "input_error", "invalid_request", "chunk_size_exceeded" -> fail(providerError("PROVIDER_PROTOCOL_ERROR", message));
                    case "insufficient_audio_activity" -> fail(providerError("NO_SPEECH", message));
                    case "transcriber_error" -> fail(providerError("PROVIDER_TRANSCRIPTION_ERROR", message));
                    case "error" -> fail(providerError("PROVIDER_ERROR", message));
                    default -> { }
                }
            } catch (SpeechToTextProviderException exception) {
                fail(exception);
            } catch (Exception exception) {
                fail(new SpeechToTextProviderException("PROVIDER_PROTOCOL_ERROR", "ElevenLabs returned an invalid response.", exception));
            }
        }

        private double confidenceFromWords(JsonNode words) {
            double total = 0;
            int count = 0;
            for (JsonNode word : words) {
                if (word.has("logprob") && word.path("logprob").isNumber()) {
                    total += Math.exp(word.path("logprob").asDouble());
                    count++;
                }
            }
            return count == 0 ? 0.0 : total / count;
        }

        private SpeechToTextProviderException providerError(String code, JsonNode message) {
            String safeMessage = message.path("error").isTextual() ? message.path("error").asText() : "ElevenLabs rejected the realtime STT request.";
            return new SpeechToTextProviderException(code, safeMessage);
        }

        private void fail(SpeechToTextProviderException exception) {
            terminal.completeExceptionally(exception);
            listener.onError(exception);
            closeSocket();
        }

        private SpeechToTextProviderException unavailable(String message) {
            return new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", message);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "ElevenLabs realtime WebSocket failed.", error));
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (finalReceived.isDone()) terminal.complete(null);
            else terminal.completeExceptionally(new SpeechToTextProviderException(
                    "PROVIDER_UNAVAILABLE", "ElevenLabs closed the stream before returning a committed transcript."));
            return CompletableFuture.completedFuture(null);
        }
    }
}
