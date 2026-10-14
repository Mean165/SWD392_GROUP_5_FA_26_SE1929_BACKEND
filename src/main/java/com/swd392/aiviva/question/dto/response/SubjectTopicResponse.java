package com.swd392.aiviva.question.dto.response;

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
public class SubjectTopicResponse {
    private UUID topicId;
    private UUID createdBy;
    private String topicCode;
    private String topicName;
    private String description;
    private OffsetDateTime createdAt;
}
