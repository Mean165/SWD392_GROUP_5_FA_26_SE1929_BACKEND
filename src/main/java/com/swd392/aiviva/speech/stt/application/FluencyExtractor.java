package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.speech.stt.domain.FluencySignals;
import com.swd392.aiviva.speech.stt.domain.STTWord;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FluencyExtractor {

    private static final Set<String> FILLERS = Set.of(
            "ờ", "ừm", "ừ", "à", "kiểu", "um", "uh", "erm", "like");

    public FluencySignals extract(List<STTWord> words, Long responseLatencyMs, long pauseMinimumMs) {
        List<STTWord> recognizedWords = words == null ? List.of() : words.stream()
                .filter(word -> word.text() != null)
                .filter(word -> word.type() == null || "word".equalsIgnoreCase(word.type()))
                .toList();
        Integer fillerCount = countFillers(recognizedWords);
        if (recognizedWords.isEmpty() || recognizedWords.stream().anyMatch(word -> word.startMs() == null || word.endMs() == null)) {
            return new FluencySignals(nonNegative(responseLatencyMs), null, null, null, null, fillerCount);
        }

        long first = recognizedWords.get(0).startMs();
        long last = recognizedWords.get(recognizedWords.size() - 1).endMs();
        long duration = Math.max(0, last - first);
        int pauses = 0;
        long longestPause = 0;
        for (int i = 1; i < recognizedWords.size(); i++) {
            long gap = Math.max(0, recognizedWords.get(i).startMs() - recognizedWords.get(i - 1).endMs());
            if (gap >= pauseMinimumMs) {
                pauses++;
                longestPause = Math.max(longestPause, gap);
            }
        }
        Double wordsPerMinute = duration == 0 ? null : recognizedWords.size() * 60_000.0 / duration;
        return new FluencySignals(nonNegative(responseLatencyMs), duration, wordsPerMinute,
                pauses, longestPause, fillerCount);
    }

    private Integer countFillers(List<STTWord> words) {
        int count = 0;
        for (STTWord word : words) {
            String normalized = Normalizer.normalize(word.text(), Normalizer.Form.NFC)
                    .toLowerCase(Locale.ROOT).replaceAll("^[\\p{Punct}]+|[\\p{Punct}]+$", "");
            if (FILLERS.contains(normalized)) count++;
        }
        return count;
    }

    private Long nonNegative(Long value) {
        return value == null ? null : Math.max(0, value);
    }
}
