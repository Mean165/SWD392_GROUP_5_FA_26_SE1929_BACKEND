package com.swd392.aiviva.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "acoustic_fluency_metric")
public class AcousticFluencyMetric implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "metric_id", nullable = false)
    private UUID metricId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", unique = true, nullable = false)
    private InteractionTurn turn;

    @Column(name = "words_per_minute", precision = 5, scale = 2)
    private BigDecimal wordsPerMinute;

    @Column(name = "hesitation_pauses_count")
    private Integer hesitationPausesCount;

    @Column(name = "stt_confidence_score", precision = 4, scale = 3)
    private BigDecimal sttConfidenceScore;

    @Column(name = "speech_clarity_score", precision = 4, scale = 2)
    private BigDecimal speechClarityScore;
}
