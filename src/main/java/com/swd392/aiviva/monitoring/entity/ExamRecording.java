package com.swd392.aiviva.monitoring.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.monitoring.enums.RecordingType;
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
@Table(name = "exam_recordings")
public class ExamRecording extends BaseEntity {

    private Long examId;
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    private RecordingType recordingType;
}

