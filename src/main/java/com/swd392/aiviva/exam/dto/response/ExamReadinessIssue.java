package com.swd392.aiviva.exam.dto.response;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamReadinessIssue {
    private String code;
    private String bloomLevel;
    private Long have;
    private Long need;
    private Integer maxPercent;
    private Integer minimumAdditionalQuestions;
    private Boolean minimumIsUpperBound;
    private Map<String, Integer> additionalQuestionsByBloom;
}
