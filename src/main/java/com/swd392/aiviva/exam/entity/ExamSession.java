package com.swd392.aiviva.exam.entity;

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
import java.io.Serializable;
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
@Table(name = "exam_session")
public class ExamSession implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "session_name", nullable = false)
    private String sessionName;

    @Column(name = "max_main_questions", nullable = false)
    private Integer maxMainQuestions;

    @Column(name = "max_followup_per_question", nullable = false)
    private Integer maxFollowupPerQuestion;

    @Column(name = "time_limit_minutes", nullable = false)
    private Integer timeLimitMinutes;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
