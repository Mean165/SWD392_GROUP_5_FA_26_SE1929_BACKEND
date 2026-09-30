package com.swd392.aiviva.evaluation.dto.response;

import com.swd392.aiviva.evaluation.enums.EvaluationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {

    private Long id;
    private Long interviewId;
    private Long studentId;
    private EvaluationStatus status;
}

