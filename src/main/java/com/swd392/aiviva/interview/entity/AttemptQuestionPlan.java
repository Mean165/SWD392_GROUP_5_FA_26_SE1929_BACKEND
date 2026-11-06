package com.swd392.aiviva.interview.entity;

import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "attempt_question_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptQuestionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private VivaAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", nullable = false, length = 50)
    private BloomLevel bloomLevel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selection_flags_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode selectionFlagsJson;
}
