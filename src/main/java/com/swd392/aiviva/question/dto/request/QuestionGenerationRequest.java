package com.swd392.aiviva.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGenerationRequest {

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private String subjectCode;
    private String topicCode;
    private Integer numberOfQuestions;
}

