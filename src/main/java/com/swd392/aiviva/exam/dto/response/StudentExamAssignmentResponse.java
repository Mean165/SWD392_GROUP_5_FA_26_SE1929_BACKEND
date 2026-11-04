package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StudentExamAssignmentResponse {

    public StudentExamAssignmentResponse(UUID assignmentId, OffsetDateTime scheduledTime, String assignmentStatus,
            UUID sessionId, String sessionName, String sessionStatus, Integer timeLimitMinutes,
            Integer maxMainQuestions, Integer maxFollowupPerQuestion, UUID attemptId, String attemptStatus,
            String topicName, Integer slotOrder) {
        this(assignmentId, scheduledTime, assignmentStatus, sessionId, sessionName, sessionStatus, timeLimitMinutes,
                maxMainQuestions, maxFollowupPerQuestion, attemptId, attemptStatus);
        this.topicName = topicName;
        this.slotOrder = slotOrder;
    }

    public StudentExamAssignmentResponse(UUID assignmentId, OffsetDateTime scheduledTime, String assignmentStatus,
            UUID sessionId, String sessionName, String sessionStatus, Integer timeLimitMinutes,
            Integer maxMainQuestions, Integer maxFollowupPerQuestion, UUID attemptId, String attemptStatus) {
        this.assignmentId = assignmentId;
        this.scheduledTime = scheduledTime;
        this.assignmentStatus = assignmentStatus;
        this.sessionId = sessionId;
        this.sessionName = sessionName;
        this.sessionStatus = sessionStatus;
        this.timeLimitMinutes = timeLimitMinutes;
        this.maxMainQuestions = maxMainQuestions;
        this.maxFollowupPerQuestion = maxFollowupPerQuestion;
        this.attemptId = attemptId;
        this.attemptStatus = attemptStatus;
    }

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
    private String topicName;
    private Integer slotOrder;
}
