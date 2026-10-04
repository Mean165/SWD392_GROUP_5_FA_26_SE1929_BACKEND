package com.swd392.aiviva.exam.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.exam.enums.ExamStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exams")
public class Exam extends BaseEntity {

    private String title;
    private String subjectCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private Integer totalQuestions;
    private Integer maxDepthQuestions;

    @Enumerated(EnumType.STRING)
    private ExamStatus status;
}

