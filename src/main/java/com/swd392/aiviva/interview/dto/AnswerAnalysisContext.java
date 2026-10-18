package com.swd392.aiviva.interview.dto;

import java.util.List;

public record AnswerAnalysisContext(
        String language,
        String mainQuestion,
        String currentQuestion,
        String transcript,
        List<PreviousTurn> previousTurns,
        List<RubricItem> rubric,
        int followUpsUsed,
        int maxFollowUps,
        long remainingSeconds) {

    public record PreviousTurn(String type, String question, String transcript) {
    }

    public record RubricItem(int rubricIndex, String name, String expectedKnowledgePoints, double weight) {
    }
}
