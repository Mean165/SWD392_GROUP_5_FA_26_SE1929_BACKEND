package com.swd392.aiviva.interview.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitAnswerResponse {

    private UUID turnId;
    private String transcriptText;
    private boolean completed;
    private boolean followUpGenerated;
    private InteractionTurnResponse nextTurn;
    private String answerClassification;
    private String analysisSummary;
    private List<AiAssessmentResponse> assessments;
}
