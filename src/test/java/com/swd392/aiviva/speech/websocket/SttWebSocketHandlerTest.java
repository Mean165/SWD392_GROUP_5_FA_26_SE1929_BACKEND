package com.swd392.aiviva.speech.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.auth.security.JwtService;
import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.interview.dto.request.SubmitAnswerRequest;
import com.swd392.aiviva.interview.service.InterviewService;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class SttWebSocketHandlerTest {

    private final SpeechToTextProvider provider = mock(SpeechToTextProvider.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final InterviewService interviewService = mock(InterviewService.class);
    private final SpeechToTextProperties properties = new SpeechToTextProperties();
    private final WebSocketSession socket = mock(WebSocketSession.class);
    private final UUID studentId = UUID.randomUUID();
    private final String email = "student@example.test";
    private final String token = "student-token";
    private final List<String> outgoing = new ArrayList<>();
    private final FakeStreamingSession stream = new FakeStreamingSession();
    private final Map<String, Object> attributes = new HashMap<>();
    private SpeechToTextResultListener listener;
    private SttWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        attributes.put(SttHandshakeInterceptor.USER_ID, studentId);
        attributes.put(SttHandshakeInterceptor.USER_EMAIL, email);
        attributes.put(SttHandshakeInterceptor.TOKEN, token);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("test-session");
        when(jwtService.isTokenValid(token, email)).thenReturn(true);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.openStreamingSession(eq("vi-VN"), eq(16_000), anyList(), anyString(), any())).thenAnswer(invocation -> {
            listener = invocation.getArgument(4);
            return stream;
        });
        doAnswer(invocation -> {
            outgoing.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(socket).sendMessage(any(TextMessage.class));
        handler = new SttWebSocketHandler(
                provider,
                properties,
                new InterviewConfig(),
                new SttDeviceCheckRateLimiter(properties),
                jwtService,
                interviewService,
                new ObjectMapper());
    }

    @Test
    void streamsForDeviceCheckAndEndsWithoutPersistingTranscript() throws Exception {
        sendStart("DEVICE_CHECK", null, null, "vi-VN");
        listener.onPartial("Xin chào");
        listener.onFinal("Xin chào", 0.9);
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {1, 0, 2, 0})));
        sendStop();

        assertHasMessage("\"type\":\"partial\"");
        assertHasMessage("\"type\":\"final\"");
        assertHasMessage("\"type\":\"ended\"");
        verify(interviewService, never()).submitAnswer(any());
        assertEquals(4, stream.audioBytes);
        assertTrue(stream.finished);
    }

    @Test
    void rejectsUnsupportedLanguage() throws Exception {
        sendStart("DEVICE_CHECK", null, null, "fr-FR");

        assertHasMessage("\"code\":\"UNSUPPORTED_LANGUAGE\"");
        verify(provider, never()).openStreamingSession(any(), anyInt(), anyList(), anyString(), any());
    }

    @Test
    void requiresExplicitAudioConsentBeforeProviderConnection() throws Exception {
        handler.handleTextMessage(socket, new TextMessage("{\"type\":\"start\",\"language\":\"vi-VN\","
                + "\"purpose\":\"DEVICE_CHECK\",\"sampleRate\":16000,\"encoding\":\"pcm_s16le\"}"));

        assertHasMessage("\"code\":\"CONSENT_REQUIRED\"");
        verify(provider, never()).openStreamingSession(any(), anyInt(), anyList(), anyString(), any());
    }

    @Test
    void rejectsMalformedPcmFrame() throws Exception {
        sendStart("DEVICE_CHECK", null, null, "vi-VN");
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[] {1, 2, 3})));

        assertHasMessage("\"code\":\"INVALID_AUDIO_FORMAT\"");
        assertTrue(stream.aborted);
    }

    @Test
    void stopMidstreamClosesProviderAndReturnsEnded() throws Exception {
        sendStart("DEVICE_CHECK", null, null, "vi-VN");
        sendStop();

        assertTrue(stream.finished);
        assertHasMessage("\"type\":\"ended\"");
        verify(interviewService, never()).submitAnswer(any());
    }

    @Test
    void reportsProviderTimeoutWithSpecificCode() throws Exception {
        stream.finishError = new SpeechToTextProviderException("PROVIDER_TIMEOUT", "timed out");
        sendStart("DEVICE_CHECK", null, null, "vi-VN");
        sendStop();

        assertHasMessage("\"code\":\"PROVIDER_TIMEOUT\"");
    }

    @Test
    void enforcesMaximumStreamDurationFromAudioSamples() throws Exception {
        properties.getSession().setMaxDurationSeconds(1);
        sendStart("DEVICE_CHECK", null, null, "vi-VN");
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[24_000])));
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[12_000])));

        assertHasMessage("\"code\":\"SESSION_TIMEOUT\"");
        assertTrue(stream.aborted);
    }

    @Test
    void expiredTokenClosesAnAlreadyOpenConnection() throws Exception {
        when(jwtService.isTokenValid(token, email)).thenReturn(false);
        sendStart("DEVICE_CHECK", null, null, "vi-VN");

        assertHasMessage("\"code\":\"TOKEN_EXPIRED\"");
    }

    @Test
    void examTranscriptFlowsIntoExistingInterviewSubmitPipeline() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        sendStart("EXAM", attemptId, turnId, "vi-VN");
        listener.onFinal("Câu trả lời", 0.8);
        stream.transcript = "Câu trả lời";
        handler.handleBinaryMessage(socket, new BinaryMessage(ByteBuffer.wrap(new byte[16_000])));
        sendStop();

        org.mockito.ArgumentCaptor<SubmitAnswerRequest> request =
                org.mockito.ArgumentCaptor.forClass(SubmitAnswerRequest.class);
        verify(interviewService).submitAnswer(request.capture());
        assertEquals(attemptId, request.getValue().getAttemptId());
        assertEquals(turnId, request.getValue().getTurnId());
        assertEquals("Câu trả lời", request.getValue().getTranscriptText());
        assertEquals("vi", request.getValue().getLanguage());
        assertHasMessage("\"type\":\"ended\"");
    }

    private void sendStart(String purpose, UUID attemptId, UUID turnId, String language) throws Exception {
        String optionalIds = attemptId == null ? "" : ",\"attemptId\":\"" + attemptId
                + "\",\"turnId\":\"" + turnId + "\"";
        handler.handleTextMessage(socket, new TextMessage("{\"type\":\"start\",\"language\":\""
                + language + "\",\"purpose\":\"" + purpose
                + "\",\"sampleRate\":16000,\"encoding\":\"pcm_s16le\",\"consent\":true" + optionalIds + "}"));
    }

    private void sendStop() throws Exception {
        handler.handleTextMessage(socket, new TextMessage("{\"type\":\"stop\"}"));
    }

    private void assertHasMessage(String expected) {
        assertTrue(outgoing.stream().anyMatch(message -> message.contains(expected)), outgoing.toString());
    }

    private static final class FakeStreamingSession implements SpeechToTextStreamingSession {
        private long audioBytes;
        private boolean finished;
        private boolean aborted;
        private String transcript = "Recognized answer.";
        private RuntimeException finishError;

        @Override
        public void sendAudio(byte[] audioFrame) {
            audioBytes += audioFrame.length;
        }

        @Override
        public String finish(java.time.Duration timeout) {
            finished = true;
            if (finishError != null) throw finishError;
            return transcript;
        }

        @Override
        public void abort() {
            aborted = true;
        }
    }
}
