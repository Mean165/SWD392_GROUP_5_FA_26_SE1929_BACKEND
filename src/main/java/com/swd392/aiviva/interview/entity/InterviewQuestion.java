package com.swd392.aiviva.interview.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.interview.enums.InterviewQuestionStatus;
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
@Table(name = "interview_questions")
public class InterviewQuestion extends BaseEntity {

    private Long interviewId;
    private Long questionId;
    private Integer followUpCount;
    private Integer maxFollowUpCount;

    @Enumerated(EnumType.STRING)
    private InterviewQuestionStatus status;
}

