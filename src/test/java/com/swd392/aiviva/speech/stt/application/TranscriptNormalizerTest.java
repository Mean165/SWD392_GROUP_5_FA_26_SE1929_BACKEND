package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptNormalizerTest {

    private final TranscriptNormalizer normalizer = new TranscriptNormalizer();

    @Test
    void preservesRawSeparatelyAndAppliesOnlyUnambiguousGlossaryCorrections() {
        String raw = "[laughter] lap trinh java va co so du lieu.";
        var result = normalizer.normalize(raw, List.of("lập trình Java", "cơ sở dữ liệu"), 0.8);

        assertEquals("lập trình Java va cơ sở dữ liệu.", result.textNormalized());
        assertEquals("[laughter] lap trinh java va co so du lieu.", raw);
        assertEquals(2, result.corrections().size());
        assertEquals("lap trinh java", result.corrections().get(0).rawSpan());
        assertEquals("lập trình Java", result.corrections().get(0).corrected());
    }

    @Test
    void doesNotCorrectAmbiguousAccentFoldedGlossaryEntries() {
        var result = normalizer.normalize("ma", List.of("ma", "má"), 0.8);
        assertEquals("ma", result.textNormalized());
        assertTrue(result.corrections().isEmpty());
    }

    @Test
    void normalizesUnicodeToNfcAndCollapsesWhitespace() {
        String decomposed = Normalizer.normalize("Tiếng Việt", Normalizer.Form.NFD);
        var result = normalizer.normalize("  " + decomposed + "\n rõ  ", List.of(), 0.9);
        assertEquals("Tiếng Việt rõ", result.textNormalized());
        assertEquals(Normalizer.Form.NFC, Normalizer.isNormalized(result.textNormalized(), Normalizer.Form.NFC)
                ? Normalizer.Form.NFC : Normalizer.Form.NFD);
    }
}
