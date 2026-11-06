package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swd392.aiviva.interview.ai.tts.TTSProvider;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import org.junit.jupiter.api.Test;

class TextToSpeechServiceImplTest {

    @Test
    void cachesSuccessfulAudioBySha256TextAndProviderSettings() {
        CountingProvider provider = new CountingProvider();
        TextToSpeechServiceImpl service = new TextToSpeechServiceImpl(provider);
        service.setProperties(new TextToSpeechProperties());

        byte[] first = service.synthesize("Cảm ơn em.", "vi", "wav");
        byte[] second = service.synthesize("Cảm ơn em.", "vi", "wav");

        assertArrayEquals(first, second);
        assertEquals(1, provider.calls);
        assertTrue(service.cacheKey("Cảm ơn em.", "vi", "wav").matches("[0-9a-f]{64}"));
    }

    @Test
    void cacheIdentityIncludesVoiceSpeedAndNormalizationVariant() {
        CountingProvider provider = new CountingProvider();
        TextToSpeechServiceImpl service = new TextToSpeechServiceImpl(provider);
        String first = service.cacheKey("Câu hỏi", "vi", "wav");
        provider.variant = "ngocanh|1.1|false";

        assertNotEquals(first, service.cacheKey("Câu hỏi", "vi", "wav"));
    }

    private static final class CountingProvider implements TTSProvider {
        private int calls;
        private String variant = "ngocanh|1.0|true";

        @Override
        public byte[] synthesize(String text) {
            calls++;
            return new byte[] {1, 2, 3};
        }

        @Override
        public byte[] synthesize(String text, String language, String format) {
            calls++;
            return new byte[] {1, 2, 3};
        }

        @Override
        public String providerName() {
            return "test";
        }

        @Override
        public String cacheVariant(String language, String format) {
            return variant;
        }
    }
}
