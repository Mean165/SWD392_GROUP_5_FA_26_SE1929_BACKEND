package com.swd392.aiviva.exam.attempt.entity;

import com.swd392.aiviva.exam.entity.ExamSession;
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
@Table(name = "student_attempt_allowance")
@Getter
@Setter
@NoArgsConstructor
public class StudentAttemptAllowance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allowance_id", nullable = false, updatable = false)
    private UUID allowanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamSession examSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "extra_attempts", nullable = false)
    private int extraAttempts;

    @Column(name = "reason", nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false)
    private AppUser grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
