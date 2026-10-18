package com.swd392.aiviva.interview.ai.stt;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.config.InterviewConfig;
import com.swd392.aiviva.common.config.OpenAiConfig;
import com.swd392.aiviva.common.exception.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiWhisperSttProviderAdapterTest {

    private OpenAiWhisperSttProviderAdapter provider;

    @BeforeEach
    void setUp() {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setKey("test-only");
        InterviewConfig interviewConfig = new InterviewConfig();
        interviewConfig.setMaxAudioBytes(4);
        provider = new OpenAiWhisperSttProviderAdapter(openAiConfig, interviewConfig, new ObjectMapper());
    }

    @Test
    void rejectsRemoteAudioUrlsToPreventServerSideFetches() {
        assertThrows(BusinessException.class, () -> provider.transcribe("https://example.invalid/audio.webm"));
    }

    @Test
    void rejectsUnsupportedMediaTypes() {
        assertThrows(BusinessException.class, () -> provider.transcribe("data:application/octet-stream;base64,YQ=="));
    }

    @Test
    void rejectsInvalidAndOversizedAudioBeforeCallingTheProvider() {
        assertThrows(BusinessException.class, () -> provider.transcribe("data:audio/webm;base64,!!!"));
        String tooLarge = Base64.getEncoder().encodeToString(new byte[5]);
        assertThrows(BusinessException.class, () -> provider.transcribe("data:audio/webm;base64," + tooLarge));
    }
}
