package com.swd392.aiviva.question.dto.response;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricMatrixResponse {
    private UUID subjectId;
    private UUID activeRubricId;
    private long criteriaCount;
    private Map<String, Long> approvedQuestionsByBloom;
    /** @deprecated Use subjectId and approvedQuestionsByBloom. */
    @Deprecated
    private UUID topicId;
    /** @deprecated Rubric no longer varies by Bloom level. */
    @Deprecated
    private List<Level> levels;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {
        private String bloomLevel;
        private boolean hasActiveRubric;
        private long criteriaCount;
        private long approvedQuestionCount;
        private long missingRubricQuestionCount;
    }
}
