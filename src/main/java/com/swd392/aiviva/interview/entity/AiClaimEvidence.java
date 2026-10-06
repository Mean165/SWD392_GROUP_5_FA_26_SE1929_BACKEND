package com.swd392.aiviva.interview.entity;

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
@Table(name = "ai_claim_evidence")
public class AiClaimEvidence implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false)
    private InteractionTurn turn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private AiRubricAssessment assessment;

    @Column(name = "student_claim_text", columnDefinition = "TEXT", nullable = false)
    private String studentClaimText;

    @Column(name = "exact_quote_evidence", columnDefinition = "TEXT", nullable = false)
    private String exactQuoteEvidence;

    @Column(name = "start_timestamp_ms", nullable = false)
    private Integer startTimestampMs;

    @Column(name = "end_timestamp_ms", nullable = false)
    private Integer endTimestampMs;

    @Column(name = "is_factually_correct", nullable = false)
    private Boolean isFactuallyCorrect;
}
