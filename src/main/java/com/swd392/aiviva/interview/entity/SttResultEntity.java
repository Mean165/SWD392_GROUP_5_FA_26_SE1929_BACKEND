package com.swd392.aiviva.interview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stt_result")
@Getter
@Setter
@NoArgsConstructor
public class SttResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stt_result_id", nullable = false, updatable = false)
    private UUID sttResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false, unique = true)
    private InteractionTurn turn;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "streaming_result_json", columnDefinition = "jsonb")
    private JsonNode streamingResultJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_result_json", columnDefinition = "jsonb")
    private JsonNode finalResultJson;

    @Column(name = "official_text_raw", columnDefinition = "TEXT")
    private String officialTextRaw;

    @Column(name = "official_source", length = 20)
    private String officialSource;

    @Column(name = "text_normalized", columnDefinition = "TEXT")
    private String textNormalized;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corrections_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode correctionsJson;

    @Column(name = "needs_human_review", nullable = false)
    private boolean needsHumanReview;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_reasons_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode reviewReasonsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reconciliation_json", columnDefinition = "jsonb")
    private JsonNode reconciliationJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
