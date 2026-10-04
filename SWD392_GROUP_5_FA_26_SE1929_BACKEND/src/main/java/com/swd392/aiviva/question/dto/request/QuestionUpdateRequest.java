package com.swd392.aiviva.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuestionUpdateRequest {

    @NotBlank(message = "Question text cannot be blank")
    private String questionText;

    @NotBlank(message = "Bloom level cannot be blank")
    private String bloomLevel;
}
