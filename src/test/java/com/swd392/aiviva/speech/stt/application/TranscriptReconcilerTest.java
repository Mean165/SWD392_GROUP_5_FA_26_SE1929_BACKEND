package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swd392.aiviva.speech.stt.domain.STTMode;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TranscriptReconcilerTest {

    private final TranscriptReconciler reconciler = new TranscriptReconciler();
    private final TranscriptNormalizer normalizer = new TranscriptNormalizer();

    @Test
    void choosesBatchAsOfficialAndMarksHighSimilarityForAnalysisReuse() {
        STTResult streaming = result("Google", "The answer is forty two.", 0.91);
        STTResult batch = result("ElevenLabs", "The answer is forty-two.", 0.97);

        var result = reconciler.reconcile(UUID.randomUUID(), streaming, batch, STTMode.DUAL,
                0.95, 0.55, 0.92, normalizer, List.of());

        assertEquals("The answer is forty-two.", result.officialTextRaw());
        assertEquals("The answer is forty-two.", result.textNormalized());
        assertEquals(1.0, result.reconciliation().similarity());
        assertTrue(result.reconciliation().reusedAnalysis());
        assertFalse(result.needsHumanReview());
    }

    @Test
    void fallsBackToStreamingAndRequestsReviewOnBatchErrorOrLowConfidence() {
        STTResult streaming = result("ElevenLabs", "Maybe four", 0.2);
        STTResult batchError = new STTResult("ElevenLabs", "scribe_v2", "final", null,
                List.of(), "eng", null, "none", 2000L, 20L, List.of(), "req-1", "timeout");

        var result = reconciler.reconcile(UUID.randomUUID(), streaming, batchError, STTMode.DUAL,
                0.95, 0.55, 0.92, normalizer, List.of());

        assertEquals("Maybe four", result.officialTextRaw());
        assertEquals("STREAMING", result.officialSource().name());
        assertTrue(result.needsHumanReview());
        assertTrue(result.reviewReasons().contains("low_confidence"));
        assertTrue(result.reviewReasons().contains("batch_error"));
    }

    private STTResult result(String provider, String text, double confidence) {
        return new STTResult(provider, "scribe", "streaming", text, List.of(), "eng",
                confidence, "provider", 2000L, 100L, List.of(), "req", null);
    }
}
