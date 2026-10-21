package com.swd392.aiviva.question.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.question.ai.port.EmbeddingPort;
import com.swd392.aiviva.question.ai.port.LlmPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "question.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockQuestionAiAdapter implements LlmPort, EmbeddingPort {
    private final ObjectMapper mapper;
    public MockQuestionAiAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String generateJson(String prompt) {
        int count = 1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("QUESTION_COUNT=(\\d+)").matcher(prompt);
        if (matcher.find()) count = Math.max(1, Math.min(20, Integer.parseInt(matcher.group(1))));
        List<String> levels = new ArrayList<>();
        java.util.regex.Matcher distribution = java.util.regex.Pattern
                .compile("(?:Bloom distribution|requested Bloom counts): \\{([^}]+)}")
                .matcher(prompt);
        if (distribution.find()) {
            java.util.regex.Matcher levelMatcher = java.util.regex.Pattern.compile("(REMEMBER|UNDERSTAND|APPLY|ANALYZE)=(\\d+)").matcher(distribution.group(1));
            while (levelMatcher.find()) for (int n = 0; n < Integer.parseInt(levelMatcher.group(2)); n++) levels.add(levelMatcher.group(1));
        }
        if (levels.size() != count) { levels.clear(); for (int i = 0; i < count; i++) levels.add("UNDERSTAND"); }
        boolean english = prompt.contains(" in en.");
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(Map.of(
                    "questionText", english ? "Based on the provided material, explain key concept " + i + " and give an example."
                            : "Dựa trên tài liệu đã cung cấp, hãy giải thích khái niệm trọng tâm " + i + " và nêu một ví dụ.",
                    "referenceAnswer", english ? "State the concept, its key points in the material, and a relevant example."
                            : "Nêu đúng khái niệm, các ý chính trong tài liệu và ví dụ phù hợp.",
                    "bloomLevel", levels.get(i - 1), "estimatedDifficulty", 2.0,
                    "suggestedAnswerMinutes", 3,
                    "rubric", List.of(Map.of("name", english ? "Accuracy" : "Mức độ chính xác",
                            "description", english ? "Explains the concept and key points correctly." : "Trình bày đúng khái niệm và ý chính.", "maxPoints", 10, "weight", 1)),
                    "citationIndexes", List.of(0)));
        }
        try { return mapper.writeValueAsString(Map.of("questions", questions)); }
        catch (Exception e) { throw new IllegalStateException("Unable to create mock response", e); }
    }

    @Override
    public List<Double> embed(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            List<Double> vector = new ArrayList<>(hash.length);
            for (byte value : hash) vector.add((double) value / 128.0);
            return vector;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
