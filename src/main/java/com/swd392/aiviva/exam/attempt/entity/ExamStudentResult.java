package com.swd392.aiviva.exam.attempt.entity;

import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exam_student_results")
@Getter
@Setter
@NoArgsConstructor
public class ExamStudentResult {
    @Id
    @Column(name = "result_id", nullable = false, updatable = false)
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamSession examSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "official_attempt_id")
    private UUID officialAttemptId;

    @Column(name = "policy_applied", nullable = false)
    private String policyApplied;

    @Column(name = "computed_total", precision = 7, scale = 2)
    private BigDecimal computedTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "released", nullable = false)
    private boolean released;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
