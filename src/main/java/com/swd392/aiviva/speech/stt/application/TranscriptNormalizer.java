package com.swd392.aiviva.speech.stt.application;

import com.swd392.aiviva.speech.stt.domain.STTCorrection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TranscriptNormalizer {

    private static final Pattern EVENT_LABEL = Pattern.compile("(?:\\[[^\\]]{1,80}]|\\([^)]{1,80}\\))");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    public NormalizationResult normalize(String rawText, List<String> glossary, double minimumScore) {
        if (rawText == null || rawText.isBlank()) return new NormalizationResult(rawText, List.of());
        String nfc = Normalizer.normalize(rawText, Normalizer.Form.NFC);
        String withoutEvents = EVENT_LABEL.matcher(nfc).replaceAll(" ");
        List<STTCorrection> corrections = new ArrayList<>();
        String corrected = applyGlossary(withoutEvents, glossary, minimumScore, corrections);
        corrected = collapseWhitespace(Normalizer.normalize(corrected, Normalizer.Form.NFC));
        return new NormalizationResult(corrected, corrections);
    }

    private String applyGlossary(
            String text, List<String> glossary, double minimumScore, List<STTCorrection> corrections) {
        if (glossary == null || glossary.isEmpty()) return text;
        List<Term> terms = glossary.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> new Term(collapseWhitespace(Normalizer.normalize(value, Normalizer.Form.NFC)),
                        tokenizeFolded(value)))
                .filter(term -> !term.tokens().isEmpty())
                .sorted(Comparator.comparingInt((Term term) -> term.tokens().size()).reversed())
                .toList();
        Map<String, Long> foldedMultiplicity = new HashMap<>();
        for (Term term : terms) foldedMultiplicity.merge(String.join(" ", term.tokens()), 1L, Long::sum);

        Matcher matcher = TOKEN.matcher(text);
        List<TokenSpan> tokens = new ArrayList<>();
        while (matcher.find()) tokens.add(new TokenSpan(matcher.start(), matcher.end(), fold(matcher.group())));
        StringBuilder output = new StringBuilder(text.length());
        int tokenIndex = 0;
        int cursor = 0;
        while (tokenIndex < tokens.size()) {
            TermMatch best = null;
            boolean ambiguousBest = false;
            for (Term term : terms) {
                if (tokenIndex + term.tokens().size() > tokens.size()) continue;
                List<String> window = tokens.subList(tokenIndex, tokenIndex + term.tokens().size()).stream()
                        .map(TokenSpan::folded).toList();
                String foldedTerm = String.join(" ", term.tokens());
                if (foldedMultiplicity.getOrDefault(foldedTerm, 0L) > 1) continue;
                double score = similarity(window, term.tokens());
                if (score < minimumScore) continue;
                if (best == null || score > best.score()) {
                    best = new TermMatch(term, score);
                    ambiguousBest = false;
                } else if (Math.abs(score - best.score()) < 0.000001) {
                    ambiguousBest = true;
                }
            }
            if (best == null || ambiguousBest) {
                tokenIndex++;
                continue;
            }

            TokenSpan first = tokens.get(tokenIndex);
            TokenSpan last = tokens.get(tokenIndex + best.term().tokens().size() - 1);
            String rawSpan = text.substring(first.start(), last.end());
            output.append(text, cursor, first.start()).append(best.term().canonical());
            corrections.add(new STTCorrection(rawSpan, best.term().canonical(), "glossary_fuzzy_match", best.score()));
            cursor = last.end();
            tokenIndex += best.term().tokens().size();
        }
        if (cursor == 0) return text;
        output.append(text, cursor, text.length());
        return output.toString();
    }

    private List<String> tokenizeFolded(String value) {
        Matcher matcher = TOKEN.matcher(Normalizer.normalize(value, Normalizer.Form.NFC));
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(fold(matcher.group()));
        return result;
    }

    private String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private double similarity(List<String> left, List<String> right) {
        String a = String.join(" ", left);
        String b = String.join(" ", right);
        int max = Math.max(a.codePointCount(0, a.length()), b.codePointCount(0, b.length()));
        if (max == 0) return 1.0;
        return 1.0 - (double) distance(a, b) / max;
    }

    private int distance(String left, String right) {
        int[] a = left.codePoints().toArray();
        int[] b = right.codePoints().toArray();
        int[] previous = new int[b.length + 1];
        int[] current = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) previous[j] = j;
        for (int i = 1; i <= a.length; i++) {
            current[0] = i;
            for (int j = 1; j <= b.length; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length];
    }

    private String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    public record NormalizationResult(String textNormalized, List<STTCorrection> corrections) {
        public NormalizationResult {
            corrections = corrections == null ? List.of() : List.copyOf(corrections);
        }
    }

    private record Term(String canonical, List<String> tokens) {
    }

    private record TokenSpan(int start, int end, String folded) {
    }

    private record TermMatch(Term term, double score) {
    }
}
