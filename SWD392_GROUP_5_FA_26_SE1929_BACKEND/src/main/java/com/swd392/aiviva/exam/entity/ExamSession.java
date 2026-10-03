package com.swd392.aiviva.exam.entity;

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
@Table(name = "exam_sessions")
public class ExamSession extends BaseEntity {

    private Long examId;
    private String sessionCode;
    private String description;
}

