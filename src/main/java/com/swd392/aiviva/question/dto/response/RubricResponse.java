package com.swd392.aiviva.question.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
public class RubricResponse {
    private UUID rubricId;
    private UUID subjectId;
    /** @deprecated Rubrics are scoped to a subject, not a topic. */
    @Deprecated
    private UUID topicId;
    /** @deprecated Bloom level belongs to questions, not rubrics. */
    @Deprecated
    private String bloomLevel;
    private String name;
    private String description;
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String source;
    private List<CriterionResponse> criteria;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionResponse {
        private UUID criteriaId;
        private UUID rubricId;
        private UUID questionId;
        private String criteriaName;
        private String expectedKnowledgePoints;
        private BigDecimal weightRatio;
    }
}

