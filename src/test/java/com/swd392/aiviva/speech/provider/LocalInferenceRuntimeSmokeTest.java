package com.swd392.aiviva.speech.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.LocalInferenceProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LocalInferenceRuntimeSmokeTest {

    @Test
    void localJavaClientSynthesizesConfiguredVietnameseAndEnglishSpeakerSamples() {
        String token = System.getenv("LOCAL_INFERENCE_TOKEN");
        Assumptions.assumeTrue(token != null && !token.isBlank(), "Requires a running local inference service token.");

        LocalInferenceProperties properties = new LocalInferenceProperties();
        properties.setUrl(System.getenv().getOrDefault("LOCAL_INFERENCE_URL", "http://127.0.0.1:8000"));
        properties.setToken(token);
        LocalInferenceClient client = new LocalInferenceClient(properties, new ObjectMapper());
        assertTrue(client.health().path("tts").path("ready").asBoolean(), "Local Piper health must be ready.");

        byte[] shortVietnamese = client.synthesize("Xin chào.", "vi", "khanh_vy", 1.0, "wav");
        byte[] vietnamese = client.synthesize(
                "Xin chào, đây là kiểm tra âm thanh. Nếu bạn nghe rõ giọng nói này, loa của bạn hoạt động tốt.",
                "vi", "khanh_vy", 1.0, "wav");
        byte[] english = client.synthesize(
                "Hello, this is an audio check. If you can hear this voice clearly, your speakers are working.",
                "en", "en_US-lessac-medium", 1.0, "wav");

        assertWave(shortVietnamese);
        assertWave(vietnamese);
        assertWave(english);
    }

    private void assertWave(byte[] audio) {
        assertTrue(audio.length > 44);
        assertEquals("RIFF", new String(audio, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(audio, 8, 4, StandardCharsets.US_ASCII));
    }
}
