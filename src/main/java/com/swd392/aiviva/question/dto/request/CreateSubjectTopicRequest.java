package com.swd392.aiviva.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubjectTopicRequest {

    @NotBlank(message = "Topic code is required")
    private String topicCode;

    @NotBlank(message = "Topic name is required")
    private String topicName;

    private String description;
}
