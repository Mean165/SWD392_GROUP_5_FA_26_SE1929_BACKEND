package com.swd392.aiviva.speech.stt.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextResultListener;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockSTTProviderTest {

    @Test
    void mockRunsRealtimePartialCommittedAndBatchWithoutCredentials() {
        SpeechToTextProperties properties = new SpeechToTextProperties();
        properties.getMock().setScenario("normal");
        MockSTTProvider provider = new MockSTTProvider(new ObjectMapper(), properties);
        SpeechToTextResultListener listener = mock(SpeechToTextResultListener.class);
        var session = provider.openStreamingSession("vi-VN", 16_000, listener);

        session.sendAudio(new byte[] {0, 0, 1, 0});
        String committed = session.finish(java.time.Duration.ofSeconds(1));
        var batch = provider.transcribeFull(UUID.randomUUID(), new byte[] {0, 0}, "vi-VN", java.util.List.of("Java"));

        verify(listener).onPartial("The answer is forty two.");
        verify(listener).onFinal("The answer is forty-two.", 0.94);
        assertEquals("The answer is forty-two.", committed);
        assertEquals("The answer is forty-two.", batch.textRaw());
        assertEquals("provider", batch.confidenceSource());
        assertTrue(provider.isAvailable());
    }
}
