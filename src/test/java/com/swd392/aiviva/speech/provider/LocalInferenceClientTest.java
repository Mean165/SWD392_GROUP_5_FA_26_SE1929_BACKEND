package com.swd392.aiviva.speech.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.LocalInferenceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalInferenceClientTest {

    private static final String TOKEN = "unit-test-internal-token";
    private HttpServer server;
    private LocalInferenceClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            assertEquals(TOKEN, exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            respond(exchange, 200, "application/json", "{\"stt\":{\"ready\":true},\"tts\":{\"ready\":true}}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/stt/transcribe", exchange -> {
            assertEquals(TOKEN, exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            assertTrue(contentType.startsWith("multipart/form-data; boundary="));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("name=\"language\"\r\n\r\nvi\r\n"));
            assertTrue(body.contains("name=\"hotwords\"\r\n\r\n[\"Java\"]\r\n"));
            assertTrue(body.contains("name=\"initial_prompt\"\r\n\r\nterms\r\n"));
            assertTrue(body.contains("name=\"mode\"\r\n\r\nfinal\r\n"));
            assertTrue(body.contains("sample-audio"));
            respond(exchange, 200, "application/json", "{\"text\":\"hello\",\"text_raw\":\"hello\"}".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/tts/synthesize", exchange -> {
            assertEquals(TOKEN, exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            var body = objectMapper.readTree(exchange.getRequestBody());
            assertEquals("vi", body.path("language").asText());
            assertEquals("sample voice", body.path("voice").asText());
            assertEquals("wav", body.path("format").asText());
            respond(exchange, 200, "audio/wav", new byte[] {1, 2, 3});
        });
        server.createContext("/v1/tts/stream", exchange -> {
            assertEquals(TOKEN, exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getResponseHeaders().add("X-Sample-Rate", "22050");
            exchange.getResponseHeaders().add("X-Channels", "1");
            byte[] pcm = {0, 0, 1, 0};
            String audio = "{\"type\":\"audio\",\"audio_base64\":\""
                    + Base64.getEncoder().encodeToString(pcm) + "\"}\n";
            byte[] events = (audio + "{\"type\":\"done\",\"duration_ms\":1}\n").getBytes(StandardCharsets.UTF_8);
            respond(exchange, 200, "application/x-ndjson", events);
        });
        server.start();

        LocalInferenceProperties properties = new LocalInferenceProperties();
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken(TOKEN);
        client = new LocalInferenceClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void callsHealthMultipartTranscriptionAndPiperEndpointsWithInternalToken() {
        assertTrue(client.health().path("stt").path("ready").asBoolean());
        assertEquals("hello", client.transcribe("sample-audio".getBytes(StandardCharsets.UTF_8), "answer.wav", "vi",
                List.of("Java"), "terms").path("text_raw").asText());
        assertArrayEquals(new byte[] {1, 2, 3}, client.synthesize("hello", "vi", "sample voice", 1.0, "wav"));
    }

    @Test
    void turnsLocalPcmStreamIntoAValidWavChunkStream() {
        List<byte[]> chunks = new ArrayList<>();
        var info = client.streamSynthesis("hello", "vi", "sample voice", 1.0, chunks::add);

        assertEquals("audio/wav", info.contentType());
        assertEquals(22_050, info.sampleRate());
        assertEquals(1, info.channels());
        assertEquals(1, info.durationMillis());
        assertEquals(1, chunks.size());
        assertEquals("RIFF", new String(chunks.get(0), 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(chunks.get(0), 8, 4, StandardCharsets.US_ASCII));
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
