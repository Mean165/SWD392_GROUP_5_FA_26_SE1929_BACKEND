package com.swd392.aiviva.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestionWorkflowDtos {
    private QuestionWorkflowDtos() {}

    public record GenerateRequest(
            @NotNull UUID subjectId,
            @NotNull UUID topicId,
            @NotEmpty List<UUID> documentIds,
            @Min(1) @Max(20) int numberOfQuestions,
            @NotEmpty Map<String, @Min(0) Integer> bloomDistribution,
            @NotNull @Pattern(regexp = "vi|en") String language,
            boolean generateRubric,
            @NotNull BigDecimal rubricTotalPoints) {}

    public record CriterionInput(@NotNull @Size(min = 1, max = 255) String name,
            @NotNull @Size(min = 1, max = 4000) String description,
            @NotNull BigDecimal maxPoints,
            @NotNull BigDecimal weight) {}

    public record EditQuestionRequest(@NotNull @Size(min = 5, max = 5000) String questionText,
            @Size(max = 10000) String referenceAnswer,
            @NotNull @Pattern(regexp = "(?i)\\s*(REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE)\\s*") String bloomLevel,
            BigDecimal estimatedDifficulty,
            @Min(1) @Max(60) Integer suggestedAnswerMinutes,
            List<CriterionInput> rubric) {}

    public record BulkApproveRequest(@NotEmpty List<UUID> questionIds) {}
}
