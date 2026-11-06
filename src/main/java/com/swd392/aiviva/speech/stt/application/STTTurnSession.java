package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.speech.stt.domain.STTEvent;
import com.swd392.aiviva.speech.stt.domain.STTMode;
import com.swd392.aiviva.speech.stt.domain.STTModeProperties;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import com.swd392.aiviva.speech.stt.domain.TurnTranscript;
import com.swd392.aiviva.speech.stt.provider.STTBatchTranscriber;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.ObjectProvider;

/** Per-answer S1 coordinator. Endpointing owns the single manual finish/commit call. */
public final class STTTurnSession implements AutoCloseable {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SAMPLE = 2;

    private final UUID turnId;
    private final String language;
    private final boolean audioConsent;
    private final STTMode mode;
    private final List<String> keyterms;
    private final PcmRingBuffer audioBuffer;
    private final SpeechToTextProvider realtimeProvider;
    private final ObjectProvider<STTBatchTranscriber> batchProviders;
    private final TranscriptNormalizer normalizer;
    private final TranscriptReconciler reconciler;
    private final STTModeProperties properties;
    private final InferenceResourceTracker resourceTracker;
    private final List<STTEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> committedSegments = new CopyOnWriteArrayList<>();
    private volatile String latestPartial = "";
    private volatile Throwable streamFailure;
    private SpeechToTextStreamingSession streamingSession;
    private long totalAudioBytes;
    private boolean started;
    private boolean finished;

    public STTTurnSession(
            UUID turnId,
            String language,
            boolean audioConsent,
            STTMode mode,
            List<String> keyterms,
            SpeechToTextProvider realtimeProvider,
            ObjectProvider<STTBatchTranscriber> batchProviders,
            TranscriptNormalizer normalizer,
            TranscriptReconciler reconciler,
            STTModeProperties properties,
            InferenceResourceTracker resourceTracker) {
        this.turnId = java.util.Objects.requireNonNull(turnId);
        this.language = requireLanguage(language);
        this.audioConsent = audioConsent;
        this.mode = java.util.Objects.requireNonNull(mode);
        this.keyterms = keyterms == null ? List.of() : List.copyOf(keyterms);
        this.realtimeProvider = realtimeProvider;
        this.batchProviders = batchProviders;
        this.normalizer = normalizer;
        this.reconciler = reconciler;
        this.properties = properties;
        this.resourceTracker = resourceTracker;
        int maxBytes = Math.multiplyExact(Math.multiplyExact(properties.getMaxAudioSeconds(), SAMPLE_RATE), BYTES_PER_SAMPLE);
        this.audioBuffer = new PcmRingBuffer(maxBytes);
    }

    public synchronized void start() {
        requireConsent();
        if (started) throw new IllegalStateException("STT turn already started.");
        if (mode != STTMode.FINAL_ONLY) {
            if (!realtimeProvider.isAvailable()) {
                throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "Realtime STT provider is unavailable.");
            }
            streamingSession = realtimeProvider.openStreamingSession(language, SAMPLE_RATE, keyterms, new SpeechToTextResultListener() {
                @Override
                public void onPartial(String text) {
                    latestPartial = text == null ? "" : text;
                    events.add(new STTEvent(STTEvent.Kind.PARTIAL, turnId, latestPartial, List.of(),
                            Instant.now(), null, null, null, null));
                }

                @Override
                public void onFinal(String text, double confidence) {
                    String committed = text == null ? "" : text;
                    committedSegments.add(committed);
                    latestPartial = "";
                    events.add(new STTEvent(STTEvent.Kind.COMMITTED, turnId, committed, List.of(),
                            Instant.now(), null, confidence, null, null));
                }

                @Override
                public void onError(Throwable error) {
                    streamFailure = error;
                    events.add(new STTEvent(STTEvent.Kind.ERROR, turnId, null, List.of(),
                            Instant.now(), null, null, "PROVIDER_UNAVAILABLE", "Realtime provider failed."));
                }
            });
        }
        started = true;
    }

    public synchronized void appendAudio(byte[] pcm16MonoFrame) {
        requireConsent();
        if (!started || finished) throw new IllegalStateException("STT turn is not accepting audio.");
        if (pcm16MonoFrame == null || pcm16MonoFrame.length == 0 || (pcm16MonoFrame.length & 1) != 0) {
            throw new IllegalArgumentException("Audio must contain non-empty complete PCM16 samples.");
        }
        long maxBytes = (long) properties.getMaxAudioSeconds() * SAMPLE_RATE * BYTES_PER_SAMPLE;
        if (totalAudioBytes + pcm16MonoFrame.length > maxBytes) {
            throw new SpeechToTextProviderException("SESSION_TIMEOUT", "STT answer exceeded the configured audio limit.");
        }
        totalAudioBytes += pcm16MonoFrame.length;
        audioBuffer.append(pcm16MonoFrame);
        if (streamingSession != null) streamingSession.sendAudio(pcm16MonoFrame);
    }

    public synchronized TurnTranscript finish(List<String> glossary) {
        requireConsent();
        if (!started || finished) throw new IllegalStateException("STT turn is not active.");
        finished = true;
        long inferenceStartedAt = System.nanoTime();
        long durationMs = totalAudioBytes * 1000 / (SAMPLE_RATE * BYTES_PER_SAMPLE);
        STTResult streaming = null;
        STTResult batch = null;

        if (mode != STTMode.FINAL_ONLY) {
            streaming = finishRealtime(durationMs);
            if (streamFailure != null && streaming.error() == null) {
                streaming = withError(streaming, "realtime_stream_error");
            }
        }
        if (mode != STTMode.STREAMING_ONLY && totalAudioBytes > 0) {
            batch = finishBatch(durationMs);
        }

        long partialCount = events.stream().filter(event -> event.kind() == STTEvent.Kind.PARTIAL).count();
        long committedCount = events.stream().filter(event -> event.kind() == STTEvent.Kind.COMMITTED).count();
        boolean failed = (streaming != null && !streaming.usable()) || (batch != null && !batch.usable());
        resourceTracker.record(durationMs, 0,
                Duration.ofNanos(System.nanoTime() - inferenceStartedAt).toMillis(),
                partialCount, committedCount, failed);

        TurnTranscript result = reconciler.reconcile(turnId, streaming, batch, mode,
                properties.getReconcileThreshold(), properties.getMinimumConfidence(),
                properties.getGlossaryFixMinimumScore(), normalizer, glossary);
        events.add(new STTEvent(STTEvent.Kind.CLOSED, turnId, null, List.of(), Instant.now(), null, null, null, null));
        return result;
    }

    private STTResult finishRealtime(long durationMs) {
        if (streamingSession == null) return null;
        try {
            String providerText = streamingSession.finish(Duration.ofSeconds(properties.getFinalTimeoutSeconds()));
            String transcript = providerText == null || providerText.isBlank()
                    ? String.join(" ", committedSegments).trim() : providerText.trim();
            Double confidence = events.stream().filter(event -> event.kind() == STTEvent.Kind.COMMITTED)
                    .map(STTEvent::confidence).filter(java.util.Objects::nonNull).reduce((left, right) -> right).orElse(null);
            return new STTResult(realtimeProvider.providerName(), "realtime", "streaming", transcript,
                    List.of(), language, confidence, confidence == null ? "none" : "provider",
                    durationMs, null, keyterms, null, null);
        } catch (RuntimeException exception) {
            String error = exception instanceof SpeechToTextProviderException providerException
                    ? providerException.getErrorCode() : "realtime_stream_error";
            return new STTResult(realtimeProvider.providerName(), "realtime", "streaming",
                    committedSegments.isEmpty() ? null : String.join(" ", committedSegments),
                    List.of(), language, null, "none", durationMs, null, keyterms, null, error);
        }
    }

    private STTResult finishBatch(long durationMs) {
        STTBatchTranscriber batchProvider = batchProviders.getIfAvailable();
        if (batchProvider == null) {
            return new STTResult("unavailable", "batch", "final", null, List.of(), language,
                    null, "none", durationMs, null, keyterms, null, "batch_provider_unavailable");
        }
        try {
            PcmRingBuffer.ReplayWindow audio = audioBuffer.snapshotUncommittedFrom(0);
            STTResult result = batchProvider.transcribeFull(turnId, audio.audio(), language, keyterms);
            return result.durationMs() == null
                    ? new STTResult(result.provider(), result.model(), result.mode(), result.textRaw(), result.words(),
                            result.languageCode(), result.confidence(), result.confidenceSource(), durationMs,
                            result.latencyMs(), result.keytermsUsed(), result.requestId(), result.error())
                    : result;
        } catch (RuntimeException exception) {
            return new STTResult(batchProvider.providerName(), "batch", "final", null, List.of(), language,
                    null, "none", durationMs, null, keyterms, null, "batch_error");
        }
    }

    private STTResult withError(STTResult result, String error) {
        return new STTResult(result.provider(), result.model(), result.mode(), result.textRaw(), result.words(),
                result.languageCode(), result.confidence(), result.confidenceSource(), result.durationMs(),
                result.latencyMs(), result.keytermsUsed(), result.requestId(), error);
    }

    public List<STTEvent> eventsSnapshot() {
        return List.copyOf(events);
    }

    public String runningText() {
        String committed = String.join(" ", committedSegments).trim();
        return latestPartial.isBlank() ? committed : (committed.isBlank() ? latestPartial : committed + " " + latestPartial);
    }

    private void requireConsent() {
        if (!audioConsent) throw new SecurityException("Explicit consent is required before audio is sent to an STT provider.");
    }

    private String requireLanguage(String value) {
        if (!"vi-VN".equals(value) && !"en-US".equals(value)) {
            throw new IllegalArgumentException("STT language must be vi-VN or en-US.");
        }
        return value;
    }

    @Override
    public synchronized void close() {
        if (streamingSession != null && !finished) streamingSession.abort();
        finished = true;
    }
}
