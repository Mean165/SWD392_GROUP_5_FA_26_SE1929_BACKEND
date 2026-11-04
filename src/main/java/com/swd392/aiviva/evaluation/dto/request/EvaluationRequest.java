package com.swd392.aiviva.evaluation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {

    public EvaluationRequest(Long interviewId, Long studentId) {
        this.interviewId = interviewId;
        this.studentId = studentId;
    }

    @NotNull(message = "Interview id is required")
    private Long interviewId;

    @NotNull(message = "Student id is required")
    private Long studentId;

    private UUID questionId;
}

