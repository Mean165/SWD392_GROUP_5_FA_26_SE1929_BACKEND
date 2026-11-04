package com.swd392.aiviva.speech.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.common.exception.AttemptExpiredException;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.service.InterviewService;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class SttWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SttWebSocketHandler.class);
    private static final String CONTEXT = "stt.context";
    private static final String START_TIMEOUT = "stt.startTimeout";
    private static final int SAMPLE_RATE = 16_000;
    private static final String ENCODING = "pcm_s16le";

    private final SpeechToTextProvider provider;
    private final SpeechToTextProperties properties;
    private final InterviewConfig interviewConfig;
    private final SttDeviceCheckRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stt-session-timeouts");
        thread.setDaemon(true);
        return thread;
    });
    private static final int START_TIMEOUT_SECONDS = 10;

    public SttWebSocketHandler(
            SpeechToTextProvider provider,
            SpeechToTextProperties properties,
            InterviewConfig interviewConfig,
            SttDeviceCheckRateLimiter rateLimiter,
            JwtService jwtService,
            InterviewService interviewService,
            ObjectMapper objectMapper) {
        this.provider = provider;
        this.properties = properties;
        this.interviewConfig = interviewConfig;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
        this.interviewService = interviewService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
            if (context(session) == null) {
                fail(session, "START_REQUIRED", "Send a start message within 10 seconds.", CloseStatus.POLICY_VIOLATION);
            }
        }, START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        session.getAttributes().put(START_TIMEOUT, timeout);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!tokenStillValid(session)) {
            fail(session, "TOKEN_EXPIRED", "Login token is invalid or expired.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            fail(session, "INVALID_MESSAGE", "Message must be valid JSON.", CloseStatus.BAD_DATA);
            return;
        }
        if (body == null || !body.hasNonNull("type")) {
            fail(session, "INVALID_MESSAGE", "Message type is required.", CloseStatus.BAD_DATA);
            return;
        }

        String type = body.path("type").asText();
        if ("start".equals(type)) {
            start(session, body);
        } else if ("stop".equals(type)) {
            stop(session);
        } else {
            fail(session, "INVALID_MESSAGE", "Only start and stop control messages are supported.", CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        if (!tokenStillValid(session)) {
            fail(session, "TOKEN_EXPIRED", "Login token is invalid or expired.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        SessionContext context = context(session);
        if (context == null || context.terminal.get()) {
            fail(session, "START_REQUIRED", "Send a valid start message before audio.", CloseStatus.BAD_DATA);
            return;
        }
        ByteBuffer payload = message.getPayload().asReadOnlyBuffer();
        if (payload.remaining() == 0 || (payload.remaining() & 1) != 0
                || payload.remaining() > properties.getSession().getMaxFrameBytes()) {
            fail(session, "INVALID_AUDIO_FORMAT", "Audio frames must be non-empty PCM16 and within the frame size limit.", CloseStatus.BAD_DATA);
            return;
        }
        int bytes = payload.remaining();
        long maxBytes = (long) SAMPLE_RATE * 2 * context.maxDurationSeconds;
        if (context.bytesReceived + bytes > maxBytes) {
            fail(session, "SESSION_TIMEOUT", "Audio duration limit exceeded.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        if ("EXAM".equals(context.purpose)
                && context.examAudio.size() + bytes > interviewConfig.getMaxAudioBytes()) {
            fail(session, "AUDIO_TOO_LARGE", "Exam audio exceeded the configured storage limit.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        byte[] frame = new byte[bytes];
        payload.get(frame);
        try {
            context.providerSession.sendAudio(frame);
            context.bytesReceived += bytes;
            if (context.examAudio != null) context.examAudio.writeBytes(frame);
        } catch (SpeechToTextProviderException exception) {
            fail(session, exception.getErrorCode(), exception.getMessage(), CloseStatus.SERVER_ERROR);
        } catch (RuntimeException exception) {
            fail(session, "PROVIDER_UNAVAILABLE", "Speech recognition provider failed.", CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelStartTimeout(session);
        SessionContext context = context(session);
        if (context != null && context.terminal.compareAndSet(false, true)) {
            cancelTimeout(context);
            releaseDeviceCheck(context);
            if (context.providerSession != null) {
                context.providerSession.abort();
            }
            log.info("STT WebSocket closed: studentId={}, purpose={}, closeCode={}",
                    context.studentId, context.purpose, status.getCode());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        SessionContext context = context(session);
        if (context != null) {
            if (context.terminal.compareAndSet(false, true)) {
                sendTerminalError(session, "PROVIDER_UNAVAILABLE", "Speech recognition connection failed.",
                        CloseStatus.SERVER_ERROR, context);
            } else if (context.providerSession != null) {
                context.providerSession.abort();
            }
        } else {
            sendTerminalError(session, "PROVIDER_UNAVAILABLE", "WebSocket transport failed.",
                    CloseStatus.SERVER_ERROR, null);
        }
        log.warn("STT WebSocket transport error: studentId={}, type={}",
                session.getAttributes().get(SttHandshakeInterceptor.USER_ID), exception.getClass().getSimpleName());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    private void start(WebSocketSession session, JsonNode body) {
        if (context(session) != null) {
            fail(session, "SESSION_ALREADY_STARTED", "This connection already has an STT session.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!body.path("consent").asBoolean(false)) {
            fail(session, "CONSENT_REQUIRED", "Consent is required before audio can be sent for speech recognition.", CloseStatus.POLICY_VIOLATION);
            return;
        }
        String language = body.path("language").asText("");
        if (!"vi-VN".equals(language) && !"en-US".equals(language)) {
            fail(session, "UNSUPPORTED_LANGUAGE", "Language must be vi-VN or en-US.", CloseStatus.BAD_DATA);
            return;
        }
        String purpose = body.path("purpose").asText("");
        if (!"DEVICE_CHECK".equals(purpose) && !"EXAM".equals(purpose)) {
            fail(session, "INVALID_PURPOSE", "Purpose must be DEVICE_CHECK or EXAM.", CloseStatus.BAD_DATA);
            return;
        }
        if (body.path("sampleRate").asInt(-1) != SAMPLE_RATE || !ENCODING.equals(body.path("encoding").asText())) {
            fail(session, "INVALID_AUDIO_FORMAT", "Use 16000 Hz mono pcm_s16le audio.", CloseStatus.BAD_DATA);
            return;
        }
        UUID attemptId = null;
        UUID turnId = null;
        if ("EXAM".equals(purpose)) {
            try {
                attemptId = UUID.fromString(body.path("attemptId").asText());
                turnId = UUID.fromString(body.path("turnId").asText());
            } catch (IllegalArgumentException exception) {
                fail(session, "EXAM_CONTEXT_REQUIRED", "EXAM sessions require attemptId and turnId.", CloseStatus.BAD_DATA);
                return;
            }
        }
        UUID studentId = (UUID) session.getAttributes().get(SttHandshakeInterceptor.USER_ID);
        if (!provider.isAvailable()) {
            fail(session, provider.readinessErrorCode(), "Speech recognition is not configured or ready.", CloseStatus.SERVER_ERROR);
            return;
        }
        if ("DEVICE_CHECK".equals(purpose) && !rateLimiter.tryStart(studentId)) {
            fail(session, "RATE_LIMITED", "Too many device-check sessions. Try again later.", CloseStatus.POLICY_VIOLATION);
            return;
        }

        cancelStartTimeout(session);
        SessionContext context = new SessionContext(studentId, purpose, attemptId, turnId);
        context.language = language;
        context.maxDurationSeconds = "DEVICE_CHECK".equals(purpose)
                ? properties.getSession().getMaxDurationSeconds()
                : interviewConfig.getAnswerTimeLimitSeconds();
        session.getAttributes().put(CONTEXT, context);
        try {
            context.providerSession = provider.openStreamingSession(language, SAMPLE_RATE, List.of(),
                    turnId == null ? UUID.randomUUID().toString() : turnId.toString(), new SpeechToTextResultListener() {
                @Override
                public void onPartial(String text) {
                    sendEvent(session, Map.of("type", "partial", "text", text));
                }

                @Override
                public void onFinal(String text, double confidence) {
                    double safeConfidence = Double.isFinite(confidence) && confidence >= 0 ? confidence : 0.0;
                    sendEvent(session, Map.of("type", "final", "text", text, "confidence", safeConfidence));
                }

                @Override
                public void onError(Throwable error) {
                    String code = error instanceof SpeechToTextProviderException providerException
                            ? providerException.getErrorCode()
                            : error.getClass().getSimpleName().contains("DeadlineExceeded")
                                    ? "PROVIDER_TIMEOUT" : "PROVIDER_UNAVAILABLE";
                    fail(session, code, "Speech recognition provider failed.", CloseStatus.SERVER_ERROR);
                }
            });
            context.timeout = timeoutExecutor.schedule(
                    () -> fail(session, "SESSION_TIMEOUT", "STT session duration limit exceeded.", CloseStatus.POLICY_VIOLATION),
                    context.maxDurationSeconds, TimeUnit.SECONDS);
        } catch (SpeechToTextProviderException exception) {
            fail(session, exception.getErrorCode(), exception.getMessage(), CloseStatus.SERVER_ERROR);
        } catch (RuntimeException exception) {
            fail(session, "PROVIDER_UNAVAILABLE", "Speech recognition provider failed to start.", CloseStatus.SERVER_ERROR);
        }
    }

    private void stop(WebSocketSession session) {
        SessionContext context = context(session);
        if (context == null || !context.terminal.compareAndSet(false, true)) {
            fail(session, "START_REQUIRED", "There is no active STT session to stop.", CloseStatus.BAD_DATA);
            return;
        }
        cancelTimeout(context);
        try {
            String transcript = context.providerSession.finish(
                    Duration.ofSeconds(properties.getSession().getFinishTimeoutSeconds()));
            if ("EXAM".equals(context.purpose)) {
                if (transcript == null || transcript.isBlank()) {
                    releaseDeviceCheck(context);
                    session.getAttributes().remove(CONTEXT);
                    sendEvent(session, Map.of("type", "error", "code", "NO_SPEECH", "message", "No speech was recognized."));
                    closeQuietly(session, CloseStatus.NORMAL);
                    return;
                }
                submitExamTranscript(session, context, transcript);
            }
            releaseDeviceCheck(context);
            session.getAttributes().remove(CONTEXT);
            sendEvent(session, Map.of("type", "ended"));
            closeQuietly(session, CloseStatus.NORMAL);
        } catch (SpeechToTextProviderException exception) {
            sendTerminalError(session, exception.getErrorCode(), exception.getMessage(), CloseStatus.SERVER_ERROR, context);
        } catch (AttemptExpiredException exception) {
            sendTerminalError(session, "EXAM_ATTEMPT_EXPIRED", "The exam attempt has expired.",
                    CloseStatus.POLICY_VIOLATION, context);
        } catch (AccessDeniedException exception) {
            sendTerminalError(session, "EXAM_ACCESS_DENIED", "This exam turn is not available to this student.",
                    CloseStatus.POLICY_VIOLATION, context);
        } catch (BusinessException exception) {
            sendTerminalError(session, "EXAM_SUBMISSION_REJECTED", exception.getMessage(),
                    CloseStatus.POLICY_VIOLATION, context);
        } catch (Exception exception) {
            sendTerminalError(session, "EXAM_SUBMISSION_FAILED", "The transcript could not be submitted for grading.",
                    CloseStatus.SERVER_ERROR, context);
        }
    }

    private void submitExamTranscript(WebSocketSession session, SessionContext context, String transcript) {
        String email = (String) session.getAttributes().get(SttHandshakeInterceptor.USER_EMAIL);
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_ST")));
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            int durationSeconds = Math.max(1, Math.toIntExact(context.bytesReceived / (SAMPLE_RATE * 2)));
            if (context.examAudio != null && context.examAudio.size() > 0) {
                interviewService.storeTurnAudio(context.attemptId, context.turnId,
                        toPcmWav(context.examAudio.toByteArray()), "audio/wav");
            }
            interviewService.submitAnswer(SubmitAnswerRequest.builder()
                    .attemptId(context.attemptId)
                    .turnId(context.turnId)
                    .transcriptText(transcript)
                    .audioDurationSeconds(durationSeconds)
                    .language("vi-VN".equals(context.language) ? "vi" : "en")
                    .build());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private byte[] toPcmWav(byte[] pcm) {
        ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + pcm.length);
        writeAscii(wav, "RIFF"); writeLittleEndian32(wav, 36 + pcm.length); writeAscii(wav, "WAVEfmt ");
        writeLittleEndian32(wav, 16); writeLittleEndian16(wav, 1); writeLittleEndian16(wav, 1);
        writeLittleEndian32(wav, SAMPLE_RATE); writeLittleEndian32(wav, SAMPLE_RATE * 2);
        writeLittleEndian16(wav, 2); writeLittleEndian16(wav, 16); writeAscii(wav, "data");
        writeLittleEndian32(wav, pcm.length); wav.writeBytes(pcm);
        return wav.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void writeLittleEndian16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff); out.write((value >>> 8) & 0xff);
    }

    private void writeLittleEndian32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff); out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff); out.write((value >>> 24) & 0xff);
    }

    private boolean tokenStillValid(WebSocketSession session) {
        Object token = session.getAttributes().get(SttHandshakeInterceptor.TOKEN);
        Object email = session.getAttributes().get(SttHandshakeInterceptor.USER_EMAIL);
        return token instanceof String jwt && email instanceof String username
                && jwtService.isTokenValid(jwt, username);
    }

    private SessionContext context(WebSocketSession session) {
        Object value = session.getAttributes().get(CONTEXT);
        return value instanceof SessionContext context ? context : null;
    }

    private void fail(WebSocketSession session, String code, String message, CloseStatus closeStatus) {
        SessionContext context = context(session);
        if (context != null && !context.terminal.compareAndSet(false, true)) {
            return;
        }
        sendTerminalError(session, code, message, closeStatus, context);
    }

    private void sendTerminalError(
            WebSocketSession session, String code, String message, CloseStatus closeStatus, SessionContext context) {
        cancelStartTimeout(session);
        if (context != null) {
            cancelTimeout(context);
            releaseDeviceCheck(context);
            if (context.providerSession != null) {
                context.providerSession.abort();
            }
            session.getAttributes().remove(CONTEXT);
            log.warn("STT session ended with error: studentId={}, purpose={}, code={}",
                    context.studentId, context.purpose, code);
        } else {
            log.warn("STT connection rejected: studentId={}, code={}",
                    session.getAttributes().get(SttHandshakeInterceptor.USER_ID), code);
        }
        sendEvent(session, Map.of("type", "error", "code", code, "message", message));
        closeQuietly(session, closeStatus);
    }

    private void sendEvent(WebSocketSession session, Map<String, ?> event) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException exception) {
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void cancelTimeout(SessionContext context) {
        ScheduledFuture<?> timeout = context.timeout;
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    private void cancelStartTimeout(WebSocketSession session) {
        Object timeout = session.getAttributes().remove(START_TIMEOUT);
        if (timeout instanceof ScheduledFuture<?> future) {
            future.cancel(false);
        }
    }

    private void releaseDeviceCheck(SessionContext context) {
        if ("DEVICE_CHECK".equals(context.purpose)) {
            rateLimiter.finish(context.studentId);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException ignored) {
                // The transport may have already closed.
            }
        }
    }

    @jakarta.annotation.PreDestroy
    public void stopTimeoutExecutor() {
        timeoutExecutor.shutdownNow();
    }

    private static final class SessionContext {
        private final UUID studentId;
        private final String purpose;
        private final UUID attemptId;
        private final UUID turnId;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile String language;
        private volatile SpeechToTextStreamingSession providerSession;
        private volatile ScheduledFuture<?> timeout;
        private volatile long bytesReceived;
        private volatile int maxDurationSeconds;
        private final ByteArrayOutputStream examAudio;

        private SessionContext(UUID studentId, String purpose, UUID attemptId, UUID turnId) {
            this.studentId = studentId;
            this.purpose = purpose;
            this.attemptId = attemptId;
            this.turnId = turnId;
            this.examAudio = "EXAM".equals(purpose) ? new ByteArrayOutputStream() : null;
        }
    }
}
