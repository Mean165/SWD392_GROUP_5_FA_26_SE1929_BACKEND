package com.swd392.aiviva.exam.attempt.entity;

import com.swd392.aiviva.exam.entity.ExamSession;
import com.swd392.aiviva.exam.entity.StudentSessionAssignment;
import com.swd392.aiviva.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamSession examSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private StudentSessionAssignment assignment;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "slot_order")
    private Integer slotOrder;

    @Column(name = "scheduled_time")
    private OffsetDateTime scheduledTime;

    @Column(name = "interview_session_id", unique = true)
    private UUID interviewSessionId;

    @Column(name = "counted", nullable = false)
    private boolean counted;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private AppUser voidedBy;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "created_by_type", nullable = false)
    private String createdByType;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
