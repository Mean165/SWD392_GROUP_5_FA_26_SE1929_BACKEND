package com.swd392.aiviva.speech.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ElevenLabsSpeechToTextProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsServerKeyInHeaderAndUsesManualCommitOnOnlyTheLastPcmFrame() throws Exception {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        properties.getElevenlabs().setApiKey("test-secret");
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class);
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        List<String> sent = new ArrayList<>();

        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.header(eq("xi-api-key"), eq("test-secret"))).thenReturn(builder);
        when(socket.sendText(any(CharSequence.class), anyBoolean())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0).toString();
            sent.add(text);
            JsonNode event = mapper.readTree(text);
            if (event.path("commit").asBoolean()) {
                listener.get().onText(socket,
                        "{\"message_type\":\"committed_transcript\",\"text\":\"Hello there\"}", true);
            }
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendClose(any(Integer.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            listener.get().onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        }).when(builder).buildAsync(any(URI.class), any(WebSocket.Listener.class));

        var provider = new ElevenLabsSpeechToTextProvider(properties, mapper, client);
        List<String> finals = new ArrayList<>();
        SpeechToTextResultListener results = new SpeechToTextResultListener() {
            @Override public void onPartial(String text) { }
            @Override public void onFinal(String text, double confidence) { finals.add(text); }
            @Override public void onError(Throwable error) { throw new AssertionError(error); }
        };
        var session = provider.openStreamingSession("en-US", 16_000, List.of("course name"), results);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(builder).buildAsync(uri.capture(), any(WebSocket.Listener.class));
        assertTrue(uri.getValue().getRawQuery().contains("commit_strategy=manual"));
        assertTrue(uri.getValue().getRawQuery().contains("language_code=en"));
        assertTrue(uri.getValue().getRawQuery().contains("keyterms=course+name"));
        assertFalse(uri.getValue().toString().contains("test-secret"));

        session.sendAudio(new byte[] {1, 0});
        session.sendAudio(new byte[] {2, 0});
        assertEquals(1, sent.size());
        assertFalse(mapper.readTree(sent.get(0)).path("commit").asBoolean());
        assertEquals("Hello there", session.finish(Duration.ofSeconds(1)));
        assertTrue(mapper.readTree(sent.get(1)).path("commit").asBoolean());
        assertEquals(List.of("Hello there"), finals);
    }

    @Test
    void rejectsUnsupportedLanguageAndInvalidPcm() {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        properties.getElevenlabs().setApiKey("test-secret");
        var provider = new ElevenLabsSpeechToTextProvider(properties, mapper, mock(HttpClient.class));

        assertThrows(SpeechToTextProviderException.class,
                () -> provider.openStreamingSession("fr-FR", 16_000, new NoopListener()));
        assertThrows(SpeechToTextProviderException.class,
                () -> provider.openStreamingSession("vi-VN", 8_000, new NoopListener()));
    }

    @Test
    void reportsMissingKeyAsNotConfiguredWithoutDisclosingCredential() {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        var provider = new ElevenLabsSpeechToTextProvider(properties, mapper, mock(HttpClient.class));

        assertFalse(provider.isAvailable());
        assertEquals("PROVIDER_NOT_CONFIGURED", provider.readinessErrorCode());
    }

    @Test
    void mapsVendorHandshakeAuthQuotaAndRateLimitStatuses() {
        assertHandshakeCode(401, "PROVIDER_AUTH_ERROR");
        assertHandshakeCode(402, "PROVIDER_QUOTA_EXCEEDED");
        assertHandshakeCode(429, "PROVIDER_RATE_LIMITED");
    }

    @Test
    void preservesSpecificVendorRealtimeErrorCategories() throws Exception {
        assertEquals("PROVIDER_AUTH_ERROR", mapVendorError("auth_error"));
        assertEquals("PROVIDER_QUOTA_EXCEEDED", mapVendorError("quota_exceeded"));
        assertEquals("PROVIDER_RATE_LIMITED", mapVendorError("commit_throttled"));
        assertEquals("PROVIDER_TERMS_NOT_ACCEPTED", mapVendorError("unaccepted_terms"));
        assertEquals("NO_SPEECH", mapVendorError("insufficient_audio_activity"));
        assertEquals("PROVIDER_TRANSCRIPTION_ERROR", mapVendorError("transcriber_error"));
        assertEquals("PROVIDER_ERROR", mapVendorError("error"));
    }

    private void assertHandshakeCode(int status, String expectedCode) {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        properties.getElevenlabs().setApiKey("test-secret");
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.header(eq("xi-api-key"), eq("test-secret"))).thenReturn(builder);
        when(response.statusCode()).thenReturn(status);
        CompletableFuture<WebSocket> failed = new CompletableFuture<>();
        failed.completeExceptionally(new WebSocketHandshakeException(response));
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class))).thenReturn(failed);

        var provider = new ElevenLabsSpeechToTextProvider(properties, mapper, client);
        SpeechToTextProviderException exception = assertThrows(SpeechToTextProviderException.class,
                () -> provider.openStreamingSession("vi-VN", 16_000, new NoopListener()));
        assertEquals(expectedCode, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("test-secret"));
    }

    private String mapVendorError(String messageType) throws Exception {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        properties.getElevenlabs().setApiKey("test-secret");
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class);
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> providerListener = new AtomicReference<>();
        AtomicReference<String> errorCode = new AtomicReference<>();
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.header(eq("xi-api-key"), eq("test-secret"))).thenReturn(builder);
        when(socket.sendClose(any(Integer.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        doAnswer(invocation -> {
            providerListener.set(invocation.getArgument(1));
            providerListener.get().onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        }).when(builder).buildAsync(any(URI.class), any(WebSocket.Listener.class));

        var provider = new ElevenLabsSpeechToTextProvider(properties, mapper, client);
        provider.openStreamingSession("en-US", 16_000, new SpeechToTextResultListener() {
            @Override public void onPartial(String text) { }
            @Override public void onFinal(String text, double confidence) { }
            @Override public void onError(Throwable error) {
                errorCode.set(((SpeechToTextProviderException) error).getErrorCode());
            }
        });
        providerListener.get().onText(socket, "{\"message_type\":\"" + messageType
                + "\",\"error\":\"safe test detail\"}", true);
        return errorCode.get();
    }

    private static final class NoopListener implements SpeechToTextResultListener {
        @Override public void onPartial(String text) { }
        @Override public void onFinal(String text, double confidence) { }
        @Override public void onError(Throwable error) { }
    }
}
