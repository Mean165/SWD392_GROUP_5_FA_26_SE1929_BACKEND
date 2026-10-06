package com.swd392.aiviva.question.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionResponse {

    private UUID questionId;
    private UUID topicId;
    private UUID createdBy;
    private String questionText;
    private String bloomLevel;
    private String sourceType;
    private String approvalStatus;
    private OffsetDateTime createdAt;
}
