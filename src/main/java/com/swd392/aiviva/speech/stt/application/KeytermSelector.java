package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.speech.stt.domain.STTMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KeytermSelector {

    private static final Logger log = LoggerFactory.getLogger(KeytermSelector.class);
    private static final java.util.regex.Pattern INVALID_KEYTERM_CHARACTERS =
            java.util.regex.Pattern.compile("[<>\\{\\}\\[\\]\\\\]");

    public Selection select(Sources sources, Limits limits) {
        return select(sources, limits, Map.of());
    }

    public Selection select(Sources sources, Limits limits, Map<String, String> abbreviations) {
        List<Candidate> ordered = new ArrayList<>();
        append(ordered, sources.keyPoints(), Source.KEY_POINT);
        append(ordered, sources.questionTerms(), Source.QUESTION);
        append(ordered, sources.subjectGlossary(), Source.SUBJECT_GLOSSARY);
        append(ordered, sources.ragTerms(), Source.RAG);
        append(ordered, sources.priorStudentTerms(), Source.PREVIOUS_TURN);

        Map<String, Candidate> unique = new LinkedHashMap<>();
        int droppedEmpty = 0;
        int droppedLong = 0;
        int droppedInvalid = 0;
        for (Candidate candidate : ordered) {
            String term = normalize(candidate.term());
            if (term.isEmpty()) {
                droppedEmpty++;
                continue;
            }
            String key = term.toLowerCase(Locale.ROOT);
            if (unique.containsKey(key)) continue;
            if (limits.batch() && INVALID_KEYTERM_CHARACTERS.matcher(term).find()) {
                droppedInvalid++;
                continue;
            }
            if (tooLong(term, limits)) {
                String abbreviation = abbreviations == null ? null : abbreviations.get(key);
                if (abbreviation != null) term = normalize(abbreviation);
                if (term.isEmpty() || tooLong(term, limits)
                        || (limits.batch() && INVALID_KEYTERM_CHARACTERS.matcher(term).find())) {
                    droppedLong++;
                    continue;
                }
            }
            String selectedKey = term.toLowerCase(Locale.ROOT);
            if (unique.containsKey(selectedKey)) continue;
            unique.put(selectedKey, new Candidate(term, candidate.source()));
            if (unique.size() >= limits.maxTerms()) break;
        }
        if (droppedEmpty + droppedLong + droppedInvalid > 0) {
            log.debug("STT keyterm selection dropped candidates: empty={}, overLimit={}, invalidCharacters={}",
                    droppedEmpty, droppedLong, droppedInvalid);
        }
        return new Selection(unique.values().stream().map(Candidate::term).toList(),
                droppedEmpty, droppedLong, droppedInvalid, limits.maxTerms(), limits.maxTermLength());
    }

    public static Limits realtimeDefaults() {
        return new Limits(50, 20, 0, false);
    }

    public static Limits batchDefaults() {
        return new Limits(1000, 50, 5, true);
    }

    private boolean tooLong(String term, Limits limits) {
        if (term.codePointCount(0, term.length()) > limits.maxTermLength()) return true;
        return limits.maxWords() > 0 && term.split("\\s+").length > limits.maxWords();
    }

    private void append(List<Candidate> target, List<String> terms, Source source) {
        if (terms == null) return;
        for (String term : terms) target.add(new Candidate(term, source));
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}\\p{Cf}]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public enum Source {
        KEY_POINT,
        QUESTION,
        SUBJECT_GLOSSARY,
        RAG,
        PREVIOUS_TURN
    }

    public record Sources(
            List<String> keyPoints,
            List<String> questionTerms,
            List<String> subjectGlossary,
            List<String> ragTerms,
            List<String> priorStudentTerms) {
    }

    public record Limits(int maxTerms, int maxTermLength, int maxWords, boolean batch) {
        public Limits(int maxTerms, int maxTermLength, int maxWords) {
            this(maxTerms, maxTermLength, maxWords, false);
        }

        public Limits {
            if (maxTerms < 1 || maxTermLength < 1 || maxWords < 0) throw new IllegalArgumentException("Keyterm limits must be positive.");
        }
    }

    public record Selection(List<String> terms, int droppedEmpty, int droppedOverLength,
            int droppedInvalid, int maxTerms, int maxTermLength) {
        public Selection {
            terms = List.copyOf(terms);
        }
    }

    private record Candidate(String term, Source source) {
    }
}
