package com.swd392.aiviva.monitoring.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.monitoring.enums.EventType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "interview_events")
public class InterviewEvent extends BaseEntity {

    private Long interviewId;
    private Long questionId;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    private LocalDateTime eventTime;
}

