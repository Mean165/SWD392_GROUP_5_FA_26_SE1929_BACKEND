package com.swd392.aiviva.speech.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ElevenLabsBatchTranscriberTest {

    @Test
    void postsDocumentedMultipartFieldsAndDoesNotGuessBatchKeytermEncoding() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/speech-to-text", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            apiKey.set(exchange.getRequestHeaders().getFirst("xi-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    {"text":"Hello world","language_code":"eng","language_probability":0.99,
                     "transcription_id":"request-1","audio_duration":1.0,
                     "words":[{"text":"Hello","start":0.0,"end":0.4,"logprob":-0.1,"type":"word"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            SpeechToTextProperties properties = new SpeechToTextProperties();
            properties.getElevenlabs().setApiKey("test-secret");
            ElevenLabsBatchTranscriber transcriber = new ElevenLabsBatchTranscriber(properties,
                    new ObjectMapper(), HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            var result = transcriber.transcribeFull(UUID.randomUUID(), new byte[32_000], "en-US", List.of("ignored-term"));

            assertEquals("test-secret", apiKey.get());
            assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
            assertTrue(body.get().contains("name=\"model_id\"\r\n\r\nscribe_v2"));
            assertTrue(body.get().contains("name=\"file_format\"\r\n\r\npcm_s16le_16"));
            assertTrue(body.get().contains("name=\"enable_logging\"\r\n\r\nfalse"));
            assertTrue(body.get().contains("filename=\"answer.pcm\""));
            assertTrue(!body.get().contains("keyterms"));
            assertEquals("Hello world", result.textRaw());
            assertEquals("eng", result.languageCode());
            assertEquals(1000L, result.durationMs());
            assertEquals("request-1", result.requestId());
            assertEquals(1, result.words().size());
            assertEquals("word_logprob", result.confidenceSource());
            assertEquals(List.of(), result.keytermsUsed());
            assertNull(result.error());
        } finally {
            server.stop(0);
        }
    }
}
