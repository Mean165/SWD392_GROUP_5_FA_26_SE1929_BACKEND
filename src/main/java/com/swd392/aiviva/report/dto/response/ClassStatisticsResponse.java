package com.swd392.aiviva.report.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassStatisticsResponse {

    private Long classId;
    private Double averageScore;
    private List<ScoreDistributionResponse> scoreDistribution;
}

