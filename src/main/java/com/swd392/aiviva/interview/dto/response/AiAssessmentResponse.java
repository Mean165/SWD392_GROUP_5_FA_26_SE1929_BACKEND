package com.swd392.aiviva.interview.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAssessmentResponse {

    private UUID assessmentId;
    private UUID criteriaId;
    private String criteriaName;
    private BigDecimal semanticSimilarity;
    private String complianceStatus;
    private BigDecimal aiSuggestedPoints;
    private String missingConcepts;
    private String aiExplanationReasoning;
}
