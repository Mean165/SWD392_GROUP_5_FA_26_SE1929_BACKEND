package com.swd392.aiviva.exam.entity;

import com.swd392.aiviva.question.enums.BloomLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_session_bloom_distribution")
@IdClass(ExamSessionBloomDistribution.Key.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSessionBloomDistribution {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", nullable = false, length = 50)
    private BloomLevel bloomLevel;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private java.util.UUID session;
        private BloomLevel bloomLevel;
    }
}
