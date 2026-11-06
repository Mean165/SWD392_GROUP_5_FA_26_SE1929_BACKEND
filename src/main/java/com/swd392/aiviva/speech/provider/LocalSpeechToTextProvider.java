package com.swd392.aiviva.speech.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderAdapter;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import com.swd392.aiviva.speech.stt.application.InferenceResourceTracker;
import com.swd392.aiviva.speech.stt.application.PcmRingBuffer;
import com.swd392.aiviva.speech.stt.application.TranscriptNormalizer;
import com.swd392.aiviva.speech.stt.application.TranscriptReconciler;
import com.swd392.aiviva.speech.stt.provider.STTBatchTranscriber;
import com.swd392.aiviva.speech.stt.domain.STTMode;
import com.swd392.aiviva.speech.stt.domain.STTModeProperties;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import com.swd392.aiviva.speech.stt.domain.STTWord;
import com.swd392.aiviva.speech.stt.domain.TurnTranscript;
import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.stt", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalSpeechToTextProvider implements SpeechToTextProvider, SpeechToTextProviderAdapter, STTBatchTranscriber {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;

    private final LocalInferenceClient inferenceClient;
    private final STTModeProperties modeProperties;
    private final SpeechToTextProperties speechProperties;
    private final TranscriptReconciler reconciler;
    private final TranscriptNormalizer normalizer;
    private final InferenceResourceTracker resourceTracker;
    private final ObjectMapper objectMapper;

    public LocalSpeechToTextProvider(LocalInferenceClient inferenceClient,
            STTModeProperties modeProperties,
            SpeechToTextProperties speechProperties, TranscriptReconciler reconciler,
            TranscriptNormalizer normalizer, InferenceResourceTracker resourceTracker,
            ObjectMapper objectMapper) {
        this.inferenceClient = inferenceClient;
        this.modeProperties = modeProperties;
        this.speechProperties = speechProperties;
        this.reconciler = reconciler;
        this.normalizer = normalizer;
        this.resourceTracker = resourceTracker;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        if (!inferenceClient.isConfigured()) return false;
        try {
            return inferenceClient.health().path("stt").path("ready").asBoolean(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String readinessErrorCode() {
        return inferenceClient.isConfigured() ? "STT_NOT_READY" : "LOCAL_INFERENCE_NOT_CONFIGURED";
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, SpeechToTextResultListener listener) {
        return openStreamingSession(language, sampleRate, List.of(), UUID.randomUUID().toString(), listener);
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, List<String> hotwords, SpeechToTextResultListener listener) {
        return openStreamingSession(language, sampleRate, hotwords, UUID.randomUUID().toString(), listener);
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, List<String> hotwords, String turnId,
            SpeechToTextResultListener listener) {
        validateLanguage(language);
        if (sampleRate != SAMPLE_RATE) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Local STT expects mono PCM16 at 16000 Hz.");
        }
        Session session = new Session(UUID.randomUUID(), turnId == null ? UUID.randomUUID().toString() : turnId,
                language, hotwords == null ? List.of() : List.copyOf(hotwords), listener, modeProperties.getParsedMode());
        if (session.mode != STTMode.FINAL_ONLY) session.openStream(false);
        return session;
    }

    @Override
    public String transcribe(String audioDataUrl) {
        return transcribe(audioDataUrl, "vi");
    }

    @Override
    public String transcribe(String audioDataUrl, String language) {
        if (audioDataUrl == null || audioDataUrl.isBlank()) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Audio is required for local transcription.");
        }
        int comma = audioDataUrl.indexOf(',');
        if (!audioDataUrl.startsWith("data:audio/") || comma < 0) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Audio must be a base64 audio data URL.");
        }
        try {
            return transcribe(Base64.getDecoder().decode(audioDataUrl.substring(comma + 1)), "answer.wav", language);
        } catch (IllegalArgumentException exception) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Audio data URL is invalid.", exception);
        }
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename, String language) {
        validateLanguageAlias(language);
        String localLanguage = "vi-VN".equalsIgnoreCase(language) || "vi".equalsIgnoreCase(language) ? "vi" : "en";
        JsonNode response = inferenceClient.transcribe(audioBytes, filename, localLanguage, List.of(), "");
        String text = response.path("text_raw").asText(response.path("text").asText("")).trim();
        if (text.isEmpty()) throw new SpeechToTextProviderException("NO_SPEECH", "No speech was recognized.");
        return text;
    }

    @Override
    public STTResult transcribeFull(UUID turnId, byte[] pcm16Audio, String language, List<String> hotwords) {
        validateLanguage(language);
        if (pcm16Audio == null || pcm16Audio.length == 0 || (pcm16Audio.length & 1) != 0) {
            return new STTResult(providerName(), "local-whisper", "final", null, List.of(), language,
                    null, "none", null, null, hotwords, null, "INVALID_AUDIO_FORMAT");
        }
        byte[] wav = pcmWav(pcm16Audio);
        JsonNode response = inferenceClient.transcribe(wav, "answer.wav",
                "vi-VN".equals(language) ? "vi" : "en", hotwords, String.join(", ", hotwords));
        return toSttResult(response, language, hotwords, pcm16Audio.length * 1000L / BYTES_PER_SECOND);
    }

    private void validateLanguage(String language) {
        if (!"vi-VN".equals(language) && !"en-US".equals(language)) {
            throw new SpeechToTextProviderException("UNSUPPORTED_LANGUAGE", "Language must be vi-VN or en-US.");
        }
    }

    private void validateLanguageAlias(String language) {
        if (!List.of("vi-VN", "en-US", "vi", "en").contains(language)) {
            throw new SpeechToTextProviderException("UNSUPPORTED_LANGUAGE", "Language must be Vietnamese or English.");
        }
    }

    private final class Session implements SpeechToTextStreamingSession, WebSocket.Listener {
        private final UUID sessionId;
        private final String turnId;
        private final String language;
        private final List<String> hotwords;
        private final SpeechToTextResultListener listener;
        private final STTMode mode;
        private final PcmRingBuffer pcmBuffer;
        private final CompletableFuture<Void> streamClosed = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private final List<String> streamingCommitted = new ArrayList<>();
        private final List<STTWord> streamWords = new ArrayList<>();
        private volatile WebSocket socket;
        private volatile SpeechToTextProviderException streamFailure;
        private volatile boolean inputEnded;
        private volatile boolean aborted;
        private volatile long replayBaseOffsetBytes;
        private long streamStartNanos;
        private long streamLatencyMs;
        private long streamQueueWaitMs;
        private long streamInferenceLatencyMs;
        private long partialCount;
        private long committedCount;

        private Session(UUID sessionId, String turnId, String language, List<String> hotwords,
                SpeechToTextResultListener listener, STTMode mode) {
            this.sessionId = sessionId;
            this.turnId = turnId;
            this.language = language;
            this.hotwords = hotwords;
            this.listener = listener;
            this.mode = mode;
            int maxBytes = Math.multiplyExact(Math.multiplyExact(
                    Math.max(1, modeProperties.getMaxAudioSeconds()), SAMPLE_RATE), 2);
            pcmBuffer = new PcmRingBuffer(maxBytes);
        }

        private void openStream(boolean replay) {
            LocalInferenceClient.WebSocketConnection connection = inferenceClient.openSttWebSocket(
                    "vi-VN".equals(language) ? "vi" : "en", sessionId.toString(), turnId, this);
            socket = connection.socket();
            streamClosed.obtrudeValue(null);
            streamStartNanos = System.nanoTime();
            var config = objectMapper.createObjectNode();
            config.put("type", "config");
            config.set("hotwords", objectMapper.valueToTree(hotwords));
            config.put("initial_prompt", String.join(", ", hotwords));
            config.put("mode", switch (mode) {
                case DUAL -> "dual";
                case FINAL_ONLY -> "final_only";
                case STREAMING_ONLY -> "streaming_only";
            });
            sendText(config.toString());
            if (replay) {
                PcmRingBuffer.ReplayWindow window;
                try {
                    window = pcmBuffer.snapshotUncommitted();
                } catch (PcmRingBuffer.ReplayWindowExceededException exception) {
                    abort();
                    throw new SpeechToTextProviderException("LOCAL_REPLAY_WINDOW_EXCEEDED",
                            "Uncommitted audio exceeded the local replay buffer.", exception);
                }
                replayBaseOffsetBytes = window.startOffset();
                byte[] remaining = window.audio();
                for (int offset = 0; offset < remaining.length; offset += speechProperties.getSession().getMaxFrameBytes()) {
                    int length = Math.min(speechProperties.getSession().getMaxFrameBytes(), remaining.length - offset);
                    byte[] frame = java.util.Arrays.copyOfRange(remaining, offset, offset + length);
                    sendBinary(frame);
                }
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public synchronized void sendAudio(byte[] frame) {
            if (inputEnded || aborted) throw new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT session is closed.");
            if (frame == null || frame.length == 0 || (frame.length & 1) != 0) {
                throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "PCM16 frame must contain complete samples.");
            }
            long maxBytes = (long) Math.max(modeProperties.getMaxAudioSeconds(), 1) * BYTES_PER_SECOND;
            if (pcmBuffer.totalBytesWritten() + frame.length > maxBytes) {
                throw new SpeechToTextProviderException("SESSION_TIMEOUT", "Audio exceeded the configured local answer limit.");
            }
            pcmBuffer.append(frame);
            if (mode != STTMode.FINAL_ONLY) {
                if (streamFailure != null) reconnectAndReplay();
                sendBinary(frame);
            }
        }

        private void sendBinary(byte[] frame) {
            WebSocket current = socket;
            if (current == null || current.isOutputClosed()) {
                streamFailure = new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT socket is not open.");
                return;
            }
            current.sendBinary(ByteBuffer.wrap(frame), true).whenComplete((ignored, error) -> {
                if (error != null) streamFailure = new SpeechToTextProviderException(
                        "LOCAL_INFERENCE_UNAVAILABLE", "Local STT service stopped accepting audio.", error);
            });
        }

        private void sendText(String text) {
            WebSocket current = socket;
            if (current == null || current.isOutputClosed()) {
                throw new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT socket is not open.");
            }
            current.sendText(text, true).whenComplete((ignored, error) -> {
                if (error != null) streamFailure = new SpeechToTextProviderException(
                        "LOCAL_INFERENCE_UNAVAILABLE", "Local STT control message failed.", error);
            });
        }

        private void reconnectAndReplay() {
            SpeechToTextProviderException lastFailure = streamFailure;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Thread.sleep(100L * (attempt + 1) + ThreadLocalRandom.current().nextLong(50));
                    openStream(true);
                    streamFailure = null;
                    return;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT reconnect was interrupted.", exception);
                } catch (RuntimeException exception) {
                    lastFailure = exception instanceof SpeechToTextProviderException stt ? stt
                            : new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT reconnect failed.", exception);
                }
            }
            throw lastFailure == null
                    ? new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local STT reconnect failed.")
                    : lastFailure;
        }

        @Override
        public String finish(Duration timeout) {
            inputEnded = true;
            long started = System.nanoTime();
            String streamedText = String.join(" ", streamingCommitted).trim();
            STTResult streaming = mode == STTMode.FINAL_ONLY ? null : streamingResult(streamedText, started);
            STTResult finalResult = null;
            if (mode != STTMode.FINAL_ONLY) {
                if (streamFailure != null) reconnectAndReplay();
                try {
                    sendText("{\"type\":\"end\"}");
                    streamClosed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    streamedText = String.join(" ", streamingCommitted).trim();
                    streaming = streamingResult(streamedText, started);
                } catch (TimeoutException exception) {
                    streamFailure = new SpeechToTextProviderException("LOCAL_INFERENCE_TIMEOUT", "Local stream did not close before timeout.", exception);
                    if (mode == STTMode.STREAMING_ONLY) throw streamFailure;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (mode == STTMode.STREAMING_ONLY) throw new SpeechToTextProviderException("LOCAL_INFERENCE_TIMEOUT", "Local stream finalization interrupted.", exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    streamFailure = cause instanceof SpeechToTextProviderException stt ? stt
                            : new SpeechToTextProviderException("LOCAL_INFERENCE_UNAVAILABLE", "Local stream failed.", cause);
                    if (mode == STTMode.STREAMING_ONLY) throw streamFailure;
                }
            }
            if (mode != STTMode.STREAMING_ONLY && pcmBuffer.totalBytesWritten() > 0) {
                finalResult = finalTranscribe(timeout);
            }

            if (mode == STTMode.STREAMING_ONLY) {
                resourceTracker.record(audioDurationMs(), streamQueueWaitMs,
                        streamInferenceLatencyMs + elapsed(started), partialCount, committedCount,
                        streaming == null || !streaming.usable());
                return streamedText;
            }
            if (mode == STTMode.FINAL_ONLY) {
                TurnTranscript transcript = reconciler.reconcile(turnIdUuid(), null, finalResult, mode,
                        modeProperties.getReconcileThreshold(), modeProperties.getMinimumConfidence(),
                        modeProperties.getGlossaryFixMinimumScore(), normalizer, List.of());
                if (transcript.officialTextRaw() != null && !transcript.officialTextRaw().isBlank()) {
                    listener.onFinal(transcript.officialTextRaw(), finalResult.confidence() == null ? 0.0 : finalResult.confidence());
                }
                resourceTracker.record(audioDurationMs(), streamQueueWaitMs,
                        streamInferenceLatencyMs + elapsed(started), 0, 1,
                        finalResult == null || !finalResult.usable());
                return transcript.officialTextRaw() == null ? "" : transcript.officialTextRaw();
            }

            TurnTranscript reconciled = reconciler.reconcile(turnIdUuid(), streaming, finalResult, mode,
                    modeProperties.getReconcileThreshold(), modeProperties.getMinimumConfidence(),
                    modeProperties.getGlossaryFixMinimumScore(), normalizer, List.of());
            if (reconciled.officialTextRaw() != null && !reconciled.officialTextRaw().isBlank()) {
                listener.onFinal(reconciled.officialTextRaw(), finalResult != null && finalResult.confidence() != null
                        ? finalResult.confidence() : 0.0);
            }
            resourceTracker.record(audioDurationMs(), streamQueueWaitMs,
                    streamInferenceLatencyMs + elapsed(started), partialCount, 1,
                    reconciled.officialTextRaw() == null || reconciled.officialTextRaw().isBlank());
            return reconciled.officialTextRaw() == null ? "" : reconciled.officialTextRaw();
        }

        @Override
        public void abort() {
            aborted = true;
            WebSocket current = socket;
            socket = null;
            if (current != null && !current.isOutputClosed()) {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "turn aborted");
            }
            streamClosed.complete(null);
        }

        private long audioDurationMs() {
            return pcmBuffer.totalBytesWritten() * 1000 / BYTES_PER_SECOND;
        }

        private long elapsed(long startedNanos) {
            return Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)).toMillis();
        }

        private UUID turnIdUuid() {
            try { return UUID.fromString(turnId); }
            catch (IllegalArgumentException exception) { return sessionId; }
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                handleEvent(fragments.toString());
                fragments.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            streamFailure = new SpeechToTextProviderException("LOCAL_INFERENCE_PROTOCOL_ERROR", "Unexpected binary local STT response.");
            streamClosed.completeExceptionally(streamFailure);
            listener.onError(streamFailure);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private void handleEvent(String payload) {
            try {
                JsonNode event = objectMapper.readTree(payload);
                switch (event.path("type").asText()) {
                    case "ready", "warning" -> { }
                    case "partial" -> {
                        partialCount++;
                        if (mode != STTMode.FINAL_ONLY) listener.onPartial(event.path("text").asText(""));
                    }
                    case "committed" -> {
                        String text = event.path("text").asText("").trim();
                        if (text.isEmpty()) break;
                        streamQueueWaitMs += event.path("queue_wait_ms").asLong(0);
                        streamInferenceLatencyMs += event.path("latency_ms").asLong(0);
                        synchronized (streamingCommitted) { streamingCommitted.add(text); }
                        committedCount++;
                        long offsetMs = replayBaseOffsetBytes * 1000 / BYTES_PER_SECOND
                                + event.path("start_ms").asLong();
                        JsonNode words = event.path("words");
                        if (words.isArray()) {
                            for (JsonNode word : words) {
                                streamWords.add(new STTWord(word.path("text").asText(""),
                                        offsetMs + word.path("start_ms").asLong(),
                                        offsetMs + word.path("end_ms").asLong(),
                                        word.path("probability").asDouble(), "word", null));
                            }
                        }
                        long committedOffset = replayBaseOffsetBytes
                                + event.path("end_ms").asLong() * BYTES_PER_SECOND / 1000;
                        if (committedOffset >= pcmBuffer.committedThroughOffset()
                                && committedOffset <= pcmBuffer.totalBytesWritten()) {
                            pcmBuffer.markCommitted(committedOffset & ~1L);
                        }
                        if (mode == STTMode.STREAMING_ONLY) {
                            listener.onFinal(text, event.path("avg_confidence").asDouble(0.0));
                        }
                    }
                    case "error" -> {
                        streamFailure = new SpeechToTextProviderException(
                                event.path("code").asText("LOCAL_INFERENCE_ERROR"),
                                event.path("message").asText("Local inference returned an error."));
                        streamClosed.completeExceptionally(streamFailure);
                        listener.onError(streamFailure);
                    }
                    case "closed" -> streamClosed.complete(null);
                    default -> { }
                }
            } catch (Exception exception) {
                streamFailure = new SpeechToTextProviderException(
                        "LOCAL_INFERENCE_PROTOCOL_ERROR", "Local STT returned an invalid event.", exception);
                streamClosed.completeExceptionally(streamFailure);
                listener.onError(streamFailure);
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            streamFailure = new SpeechToTextProviderException(
                    "LOCAL_INFERENCE_UNAVAILABLE", "Local STT socket failed.", error);
            streamClosed.completeExceptionally(streamFailure);
            listener.onError(streamFailure);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (!streamClosed.isDone() && !aborted && !inputEnded) {
                streamFailure = new SpeechToTextProviderException(
                        "LOCAL_INFERENCE_UNAVAILABLE", "Local STT socket closed unexpectedly.");
                streamClosed.completeExceptionally(streamFailure);
            }
            return CompletableFuture.completedFuture(null);
        }

        private STTResult finalTranscribe(Duration timeout) {
            try {
                return batchFinal(this);
            } catch (RuntimeException exception) {
                String code = exception instanceof SpeechToTextProviderException providerException
                        ? providerException.getErrorCode() : "LOCAL_STT_FINAL_FAILED";
                return new STTResult("local", "whisper", "final", null, List.of(), language,
                        null, "none", audioDurationMs(), null, hotwords, null, code);
            }
        }

        private STTResult streamingResult(String text, long started) {
            Double confidence = streamWords.stream().map(STTWord::confidence).filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
            return new STTResult("local", "whisper", "streaming", text, List.copyOf(streamWords), language,
                    confidence, confidence == null ? "none" : "word_probability", audioDurationMs(),
                    elapsed(started), hotwords, sessionId.toString(), streamFailure == null ? null : streamFailure.getErrorCode());
        }

    }

    private STTResult batchFinal(Session session) {
        PcmRingBuffer.ReplayWindow window = session.pcmBuffer.snapshotRetained();
        byte[] raw = window.audio();
        if (raw.length == 0) return new STTResult("local", "whisper", "final", null, List.of(), session.language,
                null, "none", 0L, 0L, session.hotwords, null, "no_audio");
        byte[] wav = pcmWav(raw);
        JsonNode response = inferenceClient.transcribe(wav, "answer.wav",
                "vi-VN".equals(session.language) ? "vi" : "en", session.hotwords, String.join(", ", session.hotwords));
        return toSttResult(response, session.language, session.hotwords, raw.length * 1000L / BYTES_PER_SECOND);
    }

    private STTResult toSttResult(JsonNode response, String language, List<String> hotwords, long audioDurationMs) {
        String textRaw = response.path("text_raw").asText(response.path("text").asText("")).trim();
        String acceptedText = response.path("text").asText("").trim();
        List<String> warnings = new ArrayList<>();
        JsonNode warningNodes = response.path("warnings");
        if (warningNodes.isArray()) warningNodes.forEach(node -> warnings.add(node.asText()));
        List<STTWord> words = new ArrayList<>();
        JsonNode wordNodes = response.path("words");
        if (wordNodes.isArray()) {
            for (JsonNode word : wordNodes) {
                words.add(new STTWord(word.path("text").asText(""), word.path("start_ms").asLong(),
                        word.path("end_ms").asLong(), word.path("probability").asDouble(), "word", null));
            }
        }
        Double confidence = response.path("avg_confidence").isNumber() ? response.path("avg_confidence").asDouble() : null;
        String error = warnings.contains("hallucination_phrase_filtered") ? "transcript_unreliable"
                : acceptedText.isBlank() ? "no_speech" : null;
        return new STTResult("local", response.path("model").asText("whisper"), "final", textRaw, words,
                response.path("language").asText(language), confidence, "model",
                audioDurationMs, response.path("latency_ms").asLong(), hotwords,
                response.path("request_id").asText(null), error,
                response.path("text_normalized").asText(acceptedText), warnings);
    }

    private byte[] pcmWav(byte[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        writeAscii(out, "RIFF"); writeLe32(out, 36 + pcm.length); writeAscii(out, "WAVEfmt ");
        writeLe32(out, 16); writeLe16(out, 1); writeLe16(out, 1); writeLe32(out, SAMPLE_RATE);
        writeLe32(out, BYTES_PER_SECOND); writeLe16(out, 2); writeLe16(out, 16); writeAscii(out, "data");
        writeLe32(out, pcm.length); out.writeBytes(pcm);
        return out.toByteArray();
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
}
