package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.speech.stt.domain.STTMode;
import com.swd392.aiviva.speech.stt.domain.STTReconciliation;
import com.swd392.aiviva.speech.stt.domain.STTResult;
import com.swd392.aiviva.speech.stt.domain.TurnTranscript;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TranscriptReconciler {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    public TurnTranscript reconcile(
            UUID turnId,
            STTResult streaming,
            STTResult batchFinal,
            STTMode mode,
            double similarityThreshold,
            double minimumConfidence,
            double glossaryFixMinimumScore,
            TranscriptNormalizer normalizer,
            List<String> glossary) {
        Double similarity = streaming != null && streaming.usable() && batchFinal != null && batchFinal.usable()
                ? tokenSimilarity(streaming.textRaw(), batchFinal.textRaw()) : null;
        STTResult official = batchFinal != null && batchFinal.usable() ? batchFinal
                : streaming != null && streaming.usable() ? streaming : null;
        TurnTranscript.OfficialSource source = official == null ? null
                : official == batchFinal ? TurnTranscript.OfficialSource.FINAL : TurnTranscript.OfficialSource.STREAMING;
        boolean reusedAnalysis = similarity != null && similarity >= similarityThreshold;
        TranscriptNormalizer.NormalizationResult normalized = official == null
                ? new TranscriptNormalizer.NormalizationResult(null, List.of())
                : normalizer.normalize(official.textRaw(), glossary, glossaryFixMinimumScore);

        List<String> reviewReasons = new ArrayList<>();
        if (official == null) reviewReasons.add("no_usable_transcript");
        if (official != null && official.confidence() != null && official.confidence() < minimumConfidence) {
            reviewReasons.add("low_confidence");
        }
        if (streaming != null && streaming.error() != null) reviewReasons.add("streaming_error");
        if (batchFinal != null && batchFinal.error() != null) reviewReasons.add("batch_error");
        if (mode == STTMode.DUAL && similarity != null && similarity < similarityThreshold) {
            reviewReasons.add("stream_final_mismatch");
        }
        if (official != null && official.languageCode() != null && streaming != null
                && streaming.languageCode() != null
                && !official.languageCode().equalsIgnoreCase(streaming.languageCode())) {
            reviewReasons.add("language_mismatch");
        }

        return new TurnTranscript(
                turnId,
                streaming,
                batchFinal,
                official == null ? null : official.textRaw(),
                source,
                normalized.textNormalized(),
                normalized.corrections(),
                !reviewReasons.isEmpty(),
                reviewReasons,
                similarity == null ? null : new STTReconciliation(similarity, reusedAnalysis));
    }

    public double tokenSimilarity(String left, String right) {
        List<String> a = comparisonTokens(left);
        List<String> b = comparisonTokens(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int[] previous = new int[b.size() + 1];
        int[] current = new int[b.size() + 1];
        for (int j = 0; j <= b.size(); j++) previous[j] = j;
        for (int i = 1; i <= a.size(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.size(); j++) {
                int substitute = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitute);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return Math.max(0.0, 1.0 - (double) previous[b.size()] / a.size());
    }

    private List<String> comparisonTokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
        normalized = NON_WORD.matcher(normalized).replaceAll(" ").trim();
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split("\\s+"));
    }
}
