package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentExamAssignmentResponse {

    private UUID assignmentId;
    private OffsetDateTime scheduledTime;
    private String assignmentStatus;
    private UUID sessionId;
    private String sessionName;
    private String sessionStatus;
    private Integer timeLimitMinutes;
    private Integer maxMainQuestions;
    private Integer maxFollowupPerQuestion;
    private UUID attemptId;
    private String attemptStatus;
}
