package com.swd392.aiviva.evaluation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {

    @NotNull(message = "Interview id is required")
    private Long interviewId;

    @NotNull(message = "Student id is required")
    private Long studentId;
}

