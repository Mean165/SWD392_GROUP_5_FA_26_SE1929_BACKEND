package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swd392.aiviva.speech.stt.domain.STTWord;
import java.util.List;
import org.junit.jupiter.api.Test;

class FluencyExtractorTest {

    private final FluencyExtractor extractor = new FluencyExtractor();

    @Test
    void calculatesWordRatePausesAndConfiguredFillers() {
        List<STTWord> words = List.of(
                new STTWord("Ờ", 100L, 300L, -0.1, "word", null),
                new STTWord("em", 1100L, 1300L, -0.1, "word", null),
                new STTWord("trả", 1400L, 1600L, -0.1, "word", null),
                new STTWord("lời", 1700L, 1900L, -0.1, "word", null));

        var signals = extractor.extract(words, 450L, 700L);

        assertEquals(450L, signals.responseLatencyMs());
        assertEquals(1800L, signals.speechDurationMs());
        assertEquals(1, signals.pauseCount());
        assertEquals(800L, signals.longestPauseMs());
        assertEquals(1, signals.fillerCount());
        assertEquals(4 * 60_000.0 / 1800, signals.wordsPerMinute(), 0.001);
    }

    @Test
    void leavesTimingSignalsNullWhenVendorDoesNotReturnWordTimestamps() {
        var signals = extractor.extract(List.of(new STTWord("Xin", null, null, null, "word", null)), null, 700);
        assertNull(signals.speechDurationMs());
        assertNull(signals.wordsPerMinute());
        assertNull(signals.pauseCount());
        assertNull(signals.longestPauseMs());
    }
}
