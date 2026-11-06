package com.swd392.aiviva.question.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricResolutionResponse {
    private String source;
    private String bloomLevel;
    private UUID rubricId;
    private List<RubricResponse.CriterionResponse> criteria;
}
