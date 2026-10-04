package com.swd392.aiviva.question.dto.response;

import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponse {
    private UUID questionId;
    private UUID topicId;
    private UUID createdBy;
    private String questionText;
    private String bloomLevel;
    private String sourceType;
    private String approvalStatus;
    private ZonedDateTime createdAt;
}
