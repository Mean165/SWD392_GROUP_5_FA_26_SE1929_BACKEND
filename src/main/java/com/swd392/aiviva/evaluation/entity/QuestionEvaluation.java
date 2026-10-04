package com.swd392.aiviva.evaluation.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import jakarta.persistence.Entity;
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
@Table(name = "question_evaluations")
public class QuestionEvaluation extends BaseEntity {

    private Long evaluationId;
    private Long questionId;
    private Double aiSuggestedScore;
    private Double lecturerFinalScore;
}

