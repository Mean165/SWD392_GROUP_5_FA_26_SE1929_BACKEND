package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import com.swd392.aiviva.speech.stt.domain.STTMode;
import com.swd392.aiviva.speech.stt.domain.STTModeProperties;
import com.swd392.aiviva.speech.stt.provider.MockSTTProvider;
import com.swd392.aiviva.speech.stt.provider.STTBatchTranscriber;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class STTTurnSessionTest {

    @Test
    void realtimeOnlyRunsWithMockAndRequiresExplicitConsent() {
        SpeechToTextProperties mockProperties = new SpeechToTextProperties();
        MockSTTProvider mockProvider = new MockSTTProvider(new ObjectMapper(), mockProperties);
        var factory = new StaticListableBeanFactory();
        factory.addBean("mockBatch", (STTBatchTranscriber) mockProvider);
        STTModeProperties modeProperties = new STTModeProperties();
        SttCostTracker costTracker = new SttCostTracker(new com.swd392.aiviva.speech.config.STTCostProperties());

        STTTurnSession noConsent = new STTTurnSession(UUID.randomUUID(), "vi-VN", false,
                STTMode.REALTIME_ONLY, List.of(), mockProvider, factory.getBeanProvider(STTBatchTranscriber.class),
                new TranscriptNormalizer(), new TranscriptReconciler(), modeProperties, costTracker);
        assertThrows(SecurityException.class, noConsent::start);

        STTTurnSession session = new STTTurnSession(UUID.randomUUID(), "vi-VN", true,
                STTMode.REALTIME_ONLY, List.of("Java"), mockProvider, factory.getBeanProvider(STTBatchTranscriber.class),
                new TranscriptNormalizer(), new TranscriptReconciler(), modeProperties, costTracker);
        session.start();
        session.appendAudio(new byte[32_000]);
        var result = session.finish(List.of("Java"));

        assertEquals("The answer is forty-two.", result.officialTextRaw());
        assertEquals("STREAMING", result.officialSource().name());
        assertTrue(mockProvider.isAvailable());
        assertTrue(costTracker.snapshot().realtimeAudioMillis() > 0);
    }

    @Test
    void dualModeReconcilesStreamAndBatchResults() {
        SpeechToTextProperties mockProperties = new SpeechToTextProperties();
        mockProperties.getMock().setScenario("mismatch");
        MockSTTProvider mockProvider = new MockSTTProvider(new ObjectMapper(), mockProperties);
        var factory = new StaticListableBeanFactory();
        factory.addBean("mockBatch", (STTBatchTranscriber) mockProvider);
        STTTurnSession session = new STTTurnSession(UUID.randomUUID(), "en-US", true,
                STTMode.DUAL, List.of(), mockProvider, factory.getBeanProvider(STTBatchTranscriber.class),
                new TranscriptNormalizer(), new TranscriptReconciler(), new STTModeProperties(),
                new SttCostTracker(new com.swd392.aiviva.speech.config.STTCostProperties()));
        session.start();
        session.appendAudio(new byte[32_000]);

        var transcript = session.finish(List.of());

        assertEquals("The answer is 2.", transcript.officialTextRaw());
        assertTrue(transcript.needsHumanReview());
        assertTrue(transcript.reviewReasons().contains("stream_final_mismatch"));
        assertTrue(transcript.reconciliation().similarity() < 1.0);
    }
}
