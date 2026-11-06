package com.swd392.aiviva.exam.dto.response;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamReadinessResponse {
    private boolean ready;
    private boolean canStart;
    private int studentCount;
    private int unscheduledCount;
    private int overlappingCount;
    private Map<String, Long> approvedQuestionsByBloom;
    private Map<String, Long> missingQuestionsByBloom;
    private List<String> warnings;
    private List<String> blockingErrors;
    private List<ExamReadinessIssue> issues;
}
