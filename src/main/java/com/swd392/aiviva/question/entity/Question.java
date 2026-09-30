package com.swd392.aiviva.question.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.question.enums.BloomLevel;
import com.swd392.aiviva.question.enums.QuestionSource;
import com.swd392.aiviva.question.enums.QuestionStatus;
import jakarta.persistence.Column;
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
@Table(name = "questions")
public class Question extends BaseEntity {

    @Column(nullable = false)
    private String content;

    private String explanation;

    @Enumerated(EnumType.STRING)
    private BloomLevel bloomLevel;

    @Enumerated(EnumType.STRING)
    private QuestionStatus status;

    @Enumerated(EnumType.STRING)
    private QuestionSource source;

    private String subjectCode;
    private String topicCode;
    private Boolean isActive;
}

