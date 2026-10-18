package com.swd392.aiviva.interview.ai.tts;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;

class OpenAiTtsProviderAdapterTest {

    @Test
    void validatesQuestionTextAndRequiresRealProviderConfiguration() {
        OpenAiConfig config = new OpenAiConfig();
        config.setMaxTextLength(5);
        OpenAiTtsProviderAdapter provider = new OpenAiTtsProviderAdapter(config);

        assertThrows(BusinessException.class, () -> provider.synthesize(" "));
        assertThrows(BusinessException.class, () -> provider.synthesize("Question too long"));
        assertThrows(ExternalServiceException.class, () -> provider.synthesize("Valid"));
    }

    @Test
    void rejectsInvalidSpeechSpeed() {
        OpenAiConfig config = new OpenAiConfig();
        config.setKey("test-only");
        OpenAiTtsProviderAdapter provider = new OpenAiTtsProviderAdapter(config);

        assertThrows(BusinessException.class, () -> provider.synthesize("Question", "vi", "alloy", 0.0, "mp3"));
    }
}
