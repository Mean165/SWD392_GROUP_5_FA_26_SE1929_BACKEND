package com.swd392.aiviva.interview.entity;

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
@Table(name = "follow_up_questions")
public class FollowUpQuestion extends BaseEntity {

    private Long interviewQuestionId;
    private String questionText;
    private Integer sequenceNumber;
}

