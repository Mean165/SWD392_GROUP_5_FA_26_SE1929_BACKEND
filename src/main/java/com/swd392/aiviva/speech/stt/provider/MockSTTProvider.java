package com.swd392.aiviva.speech.stt.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderException;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextStreamingSession;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import com.swd392.aiviva.speech.stt.domain.STTWord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.stt", name = "provider", havingValue = "mock")
public class MockSTTProvider implements SpeechToTextProvider, STTBatchTranscriber {

    private final ObjectMapper objectMapper;
    private final SpeechToTextProperties properties;

    public MockSTTProvider(ObjectMapper objectMapper, SpeechToTextProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public SpeechToTextStreamingSession openStreamingSession(
            String language, int sampleRate, SpeechToTextResultListener listener) {
        validate(language, sampleRate);
        return new MockSession(scenario(), listener);
    }

    @Override
    public STTResult transcribeFull(UUID turnId, byte[] audio, String language, List<String> keyterms) {
        if (audio == null || audio.length == 0) {
            return errorResult(turnId, language, keyterms, "no_audio");
        }
        validate(language, 16_000);
        Scenario scenario = scenario();
        if ("error".equals(scenario.mode())) return errorResult(turnId, language, keyterms, "mock_batch_error");
        String batchText = "mismatch".equalsIgnoreCase(properties.getMock().getScenario())
                ? "The answer is 2." : scenario.finalText();
        return result(turnId, language, keyterms, batchText, scenario.confidence(), audio.length / 32L);
    }

    private STTResult errorResult(UUID turnId, String language, List<String> keyterms, String error) {
        return new STTResult("mock", "mock-scribe", "final", null, List.of(), language,
                null, "none", null, 0L, keyterms, "mock-" + turnId, error);
    }

    private STTResult result(UUID turnId, String language, List<String> keyterms,
            String text, double confidence, long durationMs) {
        return new STTResult("mock", "mock-scribe", "final", text, words(text, confidence),
                language, confidence, "provider", durationMs, 0L, keyterms, "mock-" + turnId, null);
    }

    private void validate(String language, int sampleRate) {
        if (!List.of("vi-VN", "en-US").contains(language)) {
            throw new SpeechToTextProviderException("UNSUPPORTED_LANGUAGE", "Mock STT supports vi-VN and en-US.");
        }
        if (sampleRate != 16_000) {
            throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Mock STT expects 16000 Hz PCM16 mono.");
        }
    }

    private Scenario scenario() {
        String scenarioName = properties.getMock().getScenario().trim().toLowerCase(Locale.ROOT);
        String file = properties.getMock().getScenarioFile();
        if (file != null && !file.isBlank()) return loadScenario(Path.of(file), scenarioName);
        return switch (scenarioName) {
            case "silence", "empty" -> new Scenario("silence", "", "", 0.0, 1200L);
            case "vague" -> new Scenario("normal", "It depends.", "It depends.", 0.72, 1800L);
            case "low-confidence" -> new Scenario("normal", "The answer is approximately four.",
                    "The answer is approximately four.", 0.22, 2200L);
            case "disconnect" -> new Scenario("disconnect", "", "", 0.0, 1000L);
            case "mismatch" -> new Scenario("normal", "The answer is two.", "The answer is two.", 0.94, 1600L);
            case "error" -> new Scenario("error", "", "", 0.0, 1000L);
            case "normal" -> new Scenario("normal", "The answer is forty two.", "The answer is forty-two.", 0.94, 2100L);
            default -> throw new IllegalArgumentException("Unknown STT mock scenario.");
        };
    }

    private Scenario loadScenario(Path file, String scenarioName) {
        try {
            JsonNode node = objectMapper.readTree(Files.readAllBytes(file)).path(scenarioName);
            if (node.isMissingNode()) throw new IllegalArgumentException("STT mock scenario was not found in the configured JSON file.");
            return new Scenario(node.path("mode").asText("normal"), node.path("partial").asText(""),
                    node.path("committed").asText(""), node.path("confidence").asDouble(0.9),
                    node.path("duration_ms").asLong(1500));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the configured STT mock scenario file.", exception);
        }
    }

    private List<STTWord> words(String text, double confidence) {
        if (text == null || text.isBlank()) return List.of();
        String[] tokens = text.trim().split("\\s+");
        List<STTWord> words = new ArrayList<>(tokens.length);
        long start = 0;
        for (String token : tokens) {
            long duration = Math.max(120, token.codePointCount(0, token.length()) * 65L);
            words.add(new STTWord(token, start, start + duration, confidence, "word", null));
            start += duration + 80;
        }
        return List.copyOf(words);
    }

    private record Scenario(String mode, String partial, String finalText, double confidence, long durationMs) {
    }

    private final class MockSession implements SpeechToTextStreamingSession {
        private final Scenario scenario;
        private final SpeechToTextResultListener listener;
        private final AtomicBoolean emittedPartial = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();

        private MockSession(Scenario scenario, SpeechToTextResultListener listener) {
            this.scenario = scenario;
            this.listener = listener;
        }

        @Override
        public void sendAudio(byte[] audioFrame) {
            if (finished.get()) throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "Mock stream is closed.");
            if (audioFrame == null || audioFrame.length == 0 || (audioFrame.length & 1) != 0) {
                throw new SpeechToTextProviderException("INVALID_AUDIO_FORMAT", "Mock STT received an invalid PCM16 frame.");
            }
            if ("disconnect".equals(scenario.mode())) {
                if (finished.compareAndSet(false, true)) listener.onError(new IOException("mock_disconnect"));
                return;
            }
            if (!scenario.partial().isBlank() && emittedPartial.compareAndSet(false, true)) {
                listener.onPartial(scenario.partial());
            }
        }

        @Override
        public String finish(java.time.Duration timeout) {
            if (!finished.compareAndSet(false, true)) return "";
            if ("error".equals(scenario.mode())) {
                throw new SpeechToTextProviderException("PROVIDER_UNAVAILABLE", "Mock STT scenario error.");
            }
            if (!scenario.finalText().isBlank()) {
                listener.onFinal(scenario.finalText(), scenario.confidence());
                return scenario.finalText();
            }
            return "";
        }

        @Override
        public void abort() {
            finished.set(true);
        }
    }
}
