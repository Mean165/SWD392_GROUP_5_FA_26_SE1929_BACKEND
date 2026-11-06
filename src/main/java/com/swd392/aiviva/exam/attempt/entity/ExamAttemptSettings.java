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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exam_attempt_settings")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttemptSettings {
    @Id
    @Column(name = "exam_id", nullable = false)
    private java.util.UUID examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", insertable = false, updatable = false)
    private ExamSession examSession;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Column(name = "score_policy", nullable = false)
    private String scorePolicy = "LECTURER_CHOOSES";

    @Column(name = "avoid_repeat_questions", nullable = false)
    private boolean avoidRepeatQuestions = true;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Column(name = "attempt_window_start")
    private OffsetDateTime attemptWindowStart;

    @Column(name = "attempt_window_end")
    private OffsetDateTime attemptWindowEnd;

    @Column(name = "count_on_start_event", nullable = false)
    private String countOnStartEvent = "FIRST_QUESTION_ASKED";

    @Column(name = "allow_lecturer_extra_attempts", nullable = false)
    private boolean allowLecturerExtraAttempts = true;

    @Column(name = "count_abandoned", nullable = false)
    private boolean countAbandoned = true;

    @Column(name = "count_expired", nullable = false)
    private boolean countExpired;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
