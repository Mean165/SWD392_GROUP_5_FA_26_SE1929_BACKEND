package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.math.BigDecimal;
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
}
