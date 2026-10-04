package com.swd392.aiviva.exam.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.exam.enums.ParticipantStatus;
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
@Table(name = "exam_participants")
public class ExamParticipant extends BaseEntity {

    private Long examId;
    private Long studentId;

    @Enumerated(EnumType.STRING)
    private ParticipantStatus status;
}

