package com.swd392.aiviva.question.entity;

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
@Table(name = "rubric_criteria")
public class RubricCriteria implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "criteria_id", nullable = false)
    private UUID criteriaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "criteria_name", nullable = false)
    private String criteriaName;

    @Column(name = "expected_knowledge_points", columnDefinition = "TEXT", nullable = false)
    private String expectedKnowledgePoints;

    @Column(name = "weight_ratio", precision = 3, scale = 2, nullable = false)
    private BigDecimal weightRatio;
}
