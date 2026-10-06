package com.swd392.aiviva.interview.entity;

import com.swd392.aiviva.question.entity.Question;
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
@Table(name = "interaction_turn")
public class InteractionTurn implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private VivaAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_question_id")
    private Question mainQuestion;

    @Column(name = "turn_order", nullable = false)
    private Integer turnOrder;

    @Column(name = "turn_type", nullable = false)
    private String turnType;

    @Column(name = "ai_question_text", columnDefinition = "TEXT", nullable = false)
    private String aiQuestionText;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "audio_segment_url")
    private String audioSegmentUrl;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
