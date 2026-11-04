package com.swd392.aiviva.speech.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ZeroWeightTTSProviderTest {

    @Test
    void exchangesApiKeyOnHttpThenParsesFramesAndPacksWave() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (FakeZeroWeightWebSocketServer fake = new FakeZeroWeightWebSocketServer(mapper)) {
            fake.start();
            TextToSpeechProperties properties = properties(fake.sessionBaseUrl());
            ZeroWeightUsageMeter meter = new ZeroWeightUsageMeter(properties);
            ZeroWeightWebSocketClient client = new ZeroWeightWebSocketClient(mapper, meter, properties);
            ZeroWeightTTSProvider provider = new ZeroWeightTTSProvider(client, properties);

            byte[] wave = provider.synthesize("Cảm ơn em.", "vi", "wav");

            assertEquals("RIFF", new String(wave, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WAVE", new String(wave, 8, 4, StandardCharsets.US_ASCII));
            assertEquals(24_000, ByteBuffer.wrap(wave, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            assertArrayEquals(new byte[] {0x34, 0x12, 0x78, 0x56},
                    java.util.Arrays.copyOfRange(wave, 44, wave.length));
            assertEquals("server-only-test-key", fake.apiKey);
            assertFalse(fake.socketUrl.contains("server-only-test-key"));
            assertEquals("ngocanh", fake.sessionBody.path("voice_id").asText());
            assertEquals(24_000, fake.sessionBody.path("sample_rate").asInt());
            assertTrue(fake.sessionBody.path("normalize_text").asBoolean());
            assertTrue(fake.clientMessages.stream().anyMatch(message -> "config".equals(message.path("type").asText())
                    && 1.25 == message.path("speed").asDouble()));
            assertTrue(fake.clientMessages.stream().anyMatch(message -> "synthesize".equals(message.path("type").asText())
                    && "Cảm ơn em.".equals(message.path("text").asText())));
            assertTrue(fake.clientMessages.stream().anyMatch(message -> "end".equals(message.path("type").asText())));
            assertEquals(1, meter.getEstimatedCredits());
            fake.awaitSocketClose();
        }
    }

    @Test
    void retriesOnceWhenTheSingleUseSessionTokenExpires() {
        TextToSpeechProperties properties = properties("http://unused.invalid");
        FakeStreamingClient fake = new FakeStreamingClient();
        fake.expireFirstSession = true;
        ZeroWeightTTSProvider provider = new ZeroWeightTTSProvider(fake, properties);

        byte[] wave = provider.synthesize("Xin chào", "vi", "wav");

        assertEquals("RIFF", new String(wave, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(2, fake.calls);
    }

    @Test
    void refusesDoubleNormalizationInsteadOfRunningBothSteps() {
        TextToSpeechProperties properties = properties("http://unused.invalid");
        properties.getZeroWeight().setInternalNormalizationEnabled(true);
        FakeStreamingClient fake = new FakeStreamingClient();
        ZeroWeightTTSProvider provider = new ZeroWeightTTSProvider(fake, properties);

        assertThrows(com.swd392.aiviva.common.exception.ExternalServiceException.class,
                () -> provider.synthesize("Ngày 23/8/2024", "vi", "wav"));
        assertEquals(0, fake.calls);
    }

    @Test
    void parsesTheTwelveByteBigEndianHeaderAndRejectsTruncatedFrames() {
        Map<Integer, Long> sequences = new HashMap<>();
        byte[] frame = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .put((byte) 1).put((byte) 1).putShort((short) 0)
                .putInt(0).putInt(42).put(new byte[] {4, 0, 8, 0}).array();

        assertArrayEquals(new byte[] {4, 0, 8, 0}, ZeroWeightWebSocketClient.parseAudioFrame(frame, sequences));
        assertThrows(ZeroWeightProviderException.class,
                () -> ZeroWeightWebSocketClient.parseAudioFrame(new byte[11], new HashMap<>()));
    }

    private TextToSpeechProperties properties(String baseUrl) {
        TextToSpeechProperties properties = new TextToSpeechProperties();
        properties.setProvider("zeroweight");
        properties.getZeroWeight().setApiKey("server-only-test-key");
        properties.getZeroWeight().setBaseUrl(baseUrl);
        properties.getZeroWeight().setVoiceId("ngocanh");
        properties.getZeroWeight().setSpeed(1.25);
        properties.getZeroWeight().setNormalizeText(true);
        properties.getZeroWeight().setTimeoutMillis(5_000);
        return properties;
    }

    private static final class FakeStreamingClient implements ZeroWeightStreamingClient {
        private int calls;
        private boolean expireFirstSession;

        @Override
        public ZeroWeightPcmResult streamPcm(
                String text, String contextId, ZeroWeightSessionOptions options,
                java.util.function.Consumer<byte[]> pcmFrameConsumer) {
            calls++;
            if (expireFirstSession && calls == 1) {
                throw new ZeroWeightProviderException("TTS_TOKEN_EXPIRED", "expired");
            }
            pcmFrameConsumer.accept(new byte[] {1, 0, 2, 0});
            return new ZeroWeightPcmResult(1_100, "fake-gateway", 2);
        }
    }

    private static final class FakeZeroWeightWebSocketServer implements AutoCloseable {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final ObjectMapper mapper;
        private final ServerSocket webSocketServer;
        private final HttpServer httpServer;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CompletableFuture<Void> socketClosed = new CompletableFuture<>();
        private final List<JsonNode> clientMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile String apiKey;
        private volatile String socketUrl;
        private volatile JsonNode sessionBody;

        private FakeZeroWeightWebSocketServer(ObjectMapper mapper) throws IOException {
            this.mapper = mapper;
            webSocketServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext("/api/v1/realtime/stream/session", this::createSession);
            httpServer.setExecutor(executor);
        }

        private void start() {
            httpServer.start();
            executor.submit(this::serveWebSocket);
        }

        private String sessionBaseUrl() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort();
        }

        private void createSession(HttpExchange exchange) throws IOException {
            apiKey = exchange.getRequestHeaders().getFirst("X-ZW-Api-Key");
            sessionBody = mapper.readTree(exchange.getRequestBody());
            socketUrl = "ws://127.0.0.1:" + webSocketServer.getLocalPort() + "/stream?token=single-use";
            byte[] body = mapper.writeValueAsBytes(Map.of("url", socketUrl, "gateway", "fake-gateway"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        private void serveWebSocket() {
            try (var socket = webSocketServer.accept()) {
                socket.setSoTimeout(5_000);
                var input = new java.io.BufferedInputStream(socket.getInputStream());
                var output = new java.io.BufferedOutputStream(socket.getOutputStream());
                Map<String, String> headers = readHeaders(input);
                String key = headers.get("sec-websocket-key");
                String accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII)));
                output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();
                boolean synthesized = false;
                while (true) {
                    ClientFrame frame = readClientFrame(input);
                    if (frame == null || frame.opcode() == 8) {
                        writeFrame(output, 8, new byte[] {3, (byte) 232});
                        output.flush();
                        socketClosed.complete(null);
                        return;
                    }
                    if (frame.opcode() != 1) continue;
                    JsonNode message = mapper.readTree(frame.payload());
                    clientMessages.add(message);
                    String type = message.path("type").asText();
                    if ("synthesize".equals(type) && !synthesized) {
                        synthesized = true;
                        writeText(output, Map.of("type", "context_start", "context_id", message.path("context_id").asText()));
                        byte[] pcmFrame = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                                .put((byte) 1).put((byte) 1).putShort((short) 0)
                                .putInt(0).putInt(10).put(new byte[] {0x34, 0x12, 0x78, 0x56}).array();
                        writeFrame(output, 2, pcmFrame);
                        writeText(output, Map.of("type", "segment_done", "context_id", message.path("context_id").asText()));
                    }
                    if ("end".equals(type)) {
                        writeText(output, Map.of("type", "context_done", "context_id", message.path("context_id").asText()));
                    }
                    output.flush();
                }
            } catch (Exception exception) {
                socketClosed.completeExceptionally(exception);
            }
        }

        private Map<String, String> readHeaders(java.io.InputStream input) throws IOException {
            Map<String, String> headers = new HashMap<>();
            readLine(input);
            while (true) {
                String line = readLine(input);
                if (line.isEmpty()) return headers;
                int separator = line.indexOf(':');
                if (separator > 0) headers.put(line.substring(0, separator).trim().toLowerCase(),
                        line.substring(separator + 1).trim());
            }
        }

        private String readLine(java.io.InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;
            while (true) {
                int current = input.read();
                if (current < 0) throw new IOException("Unexpected socket close.");
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = line.toByteArray();
                    return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.US_ASCII);
                }
                line.write(current);
                previous = current;
            }
        }

        private ClientFrame readClientFrame(java.io.InputStream input) throws IOException {
            int first = input.read();
            if (first < 0) return null;
            int second = input.read();
            int opcode = first & 0x0f;
            long length = second & 0x7f;
            if (length == 126) length = ((input.read() & 0xff) << 8) | (input.read() & 0xff);
            else if (length == 127) throw new IOException("Test client frame unexpectedly large.");
            byte[] mask = input.readNBytes(4);
            byte[] payload = input.readNBytes(Math.toIntExact(length));
            if (mask.length != 4 || payload.length != length) throw new IOException("Truncated client frame.");
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            return new ClientFrame(opcode, payload);
        }

        private void writeText(java.io.OutputStream output, Object value) throws IOException {
            writeFrame(output, 1, mapper.writeValueAsBytes(value));
        }

        private void writeFrame(java.io.OutputStream output, int opcode, byte[] payload) throws IOException {
            output.write(0x80 | opcode);
            if (payload.length < 126) {
                output.write(payload.length);
            } else {
                output.write(126);
                output.write((payload.length >>> 8) & 0xff);
                output.write(payload.length & 0xff);
            }
            output.write(payload);
        }

        private void awaitSocketClose() throws Exception {
            socketClosed.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            httpServer.stop(0);
            try {
                webSocketServer.close();
            } catch (IOException ignored) {
            }
            executor.shutdownNow();
        }

        private record ClientFrame(int opcode, byte[] payload) {
        }
    }
}
