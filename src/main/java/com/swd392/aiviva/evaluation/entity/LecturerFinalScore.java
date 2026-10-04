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
@Table(name = "lecturer_final_scores")
public class LecturerFinalScore extends BaseEntity {

    private Long evaluationId;
    private Double score;
    private String note;
}

