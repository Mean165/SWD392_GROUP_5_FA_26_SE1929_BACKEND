package com.swd392.aiviva.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.aiviva.interview.ai.stt.SpeechToTextProviderAdapter;
import com.swd392.aiviva.interview.ai.tts.TextToSpeechProviderAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpeechAndTtsServiceCompatibilityTest {

    @Mock
    private SpeechToTextProviderAdapter sttProvider;
    @Mock
    private TextToSpeechProviderAdapter ttsProvider;
    @InjectMocks
    private SpeechToTextServiceImpl sttService;
    @InjectMocks
    private TextToSpeechServiceImpl ttsService;

    @Test
    void legacySttMethodStillUsesTheProviderLegacyMethod() {
        when(sttProvider.transcribe("data:audio/wav;base64,AA==")).thenReturn("recognized");

        assertEquals("recognized", sttService.transcribe("data:audio/wav;base64,AA=="));
        verify(sttProvider).transcribe("data:audio/wav;base64,AA==");
    }

    @Test
    void legacyTtsMethodStillUsesTheProviderLegacyMethod() {
        byte[] audio = {7, 8};
        when(ttsProvider.synthesize("sample question")).thenReturn(audio);

        assertArrayEquals(audio, ttsService.synthesize("sample question"));
        verify(ttsProvider).synthesize("sample question");
    }
}
