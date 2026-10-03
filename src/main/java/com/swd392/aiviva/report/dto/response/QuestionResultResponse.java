package com.swd392.aiviva.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResultResponse {

    private Long questionId;
    private Double score;
    private String comment;
}

