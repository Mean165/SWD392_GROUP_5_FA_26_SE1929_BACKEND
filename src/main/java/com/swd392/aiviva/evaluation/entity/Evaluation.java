package com.swd392.aiviva.evaluation.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.evaluation.enums.EvaluationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "evaluations")
public class Evaluation extends BaseEntity {

    private Long interviewId;
    private Long studentId;
    private Long questionId;

    @Enumerated(EnumType.STRING)
    private EvaluationStatus status;
}

