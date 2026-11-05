package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSessionAssignmentResponse {

    private UUID assignmentId;
    private UUID sessionId;
    private String sessionName;
    private UUID studentId;
    private String studentCode;
    private String studentName;
    private String studentEmail;
    private OffsetDateTime scheduledTime;
    private String status;
    private Integer slotOrder;
    private String cancelledReason;
    private UUID attemptId;
    private String attemptStatus;
    private BigDecimal totalAiScore;
    private BigDecimal finalTeacherScore;
    private int attemptsUsed;
    private int attemptsAllowed;
    private int attemptsRemaining;
    private int extraAttempts;
    private ExamAttemptItem currentAttempt;
    private List<ExamAttemptItem> attempts;
    private OfficialExamResult officialResult;
    private boolean removed;

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExamAttemptItem {
        private UUID examAttemptId;
        private int attemptNo;
        private String status;
        private OffsetDateTime scheduledTime;
        private Integer slotOrder;
        private UUID interviewSessionId;
        private BigDecimal aiSuggestedTotal;
        private BigDecimal finalTotal;
        private boolean counted;
        private boolean voided;
    }

    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OfficialExamResult {
        private String policy;
        private UUID officialAttemptId;
        private BigDecimal total;
        private boolean released;
    }
}
