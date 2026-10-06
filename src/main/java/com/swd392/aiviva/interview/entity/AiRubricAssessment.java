package com.swd392.aiviva.interview.entity;

import com.swd392.aiviva.question.entity.RubricCriteria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_rubric_assessment")
public class AiRubricAssessment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false)
    private InteractionTurn turn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private RubricCriteria criteria;

    @Column(name = "semantic_similarity", precision = 4, scale = 3)
    private BigDecimal semanticSimilarity;

    @Column(name = "compliance_status", nullable = false)
    private String complianceStatus;

    @Column(name = "ai_suggested_points", precision = 4, scale = 2, nullable = false)
    private BigDecimal aiSuggestedPoints;

    @Column(name = "missing_concepts", columnDefinition = "TEXT")
    private String missingConcepts;

    @Column(name = "ai_explanation_reasoning", columnDefinition = "TEXT")
    private String aiExplanationReasoning;
}
