package com.swd392.aiviva.evaluation.entity;

import com.swd392.aiviva.interview.entity.VivaAttempt;
import com.swd392.aiviva.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "final_grade_report")
public class FinalGradeReport implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", unique = true, nullable = false)
    private VivaAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by_lecturer_id")
    private AppUser finalizedByLecturer;

    @Column(name = "total_ai_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal totalAiScore;

    @Column(name = "final_teacher_score", precision = 5, scale = 2)
    private BigDecimal finalTeacherScore;

    @Column(name = "teacher_adjustment_reason", columnDefinition = "TEXT")
    private String teacherAdjustmentReason;

    @Column(name = "ai_overall_feedback", columnDefinition = "TEXT")
    private String aiOverallFeedback;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
}
