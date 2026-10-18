package com.swd392.aiviva.interview.dto;

import java.util.List;

public record AnswerAnalysisResult(
        Classification classification,
        String summary,
        List<String> missingConcepts,
        String followUpQuestion,
        List<CriterionResult> criteria) {

    public enum Classification {
        SUFFICIENT,
        PARTIAL,
        AMBIGUOUS,
        CONTRADICTORY,
        UNRELATED,
        UNASSESSABLE
    }

    public record CriterionResult(
            Integer rubricIndex,
            String criteriaName,
            Double scoreFraction,
            String complianceStatus,
            List<String> missingConcepts,
            String reasoning) {
    }
}
