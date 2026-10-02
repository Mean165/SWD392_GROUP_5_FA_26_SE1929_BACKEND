package com.swd392.aiviva.interview.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.interview.enums.AnswerStatus;
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
@Table(name = "interview_answers")
public class InterviewAnswer extends BaseEntity {

    private Long interviewQuestionId;
    private String transcript;
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    private AnswerStatus status;
}

