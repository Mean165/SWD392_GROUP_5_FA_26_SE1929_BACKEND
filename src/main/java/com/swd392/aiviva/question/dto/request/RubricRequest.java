package com.swd392.aiviva.question.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RubricRequest {
    @NotBlank(message = "Rubric name is required")
    private String name;
    private String description;
    @NotEmpty(message = "Rubric must contain at least one criterion")
    @Valid
    private List<CriterionRequest> criteria;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionRequest {
        @NotBlank(message = "Criteria name is required")
        private String criteriaName;
        @NotBlank(message = "Expected knowledge points are required")
        private String expectedKnowledgePoints;
        @NotNull(message = "Weight ratio is required")
        private BigDecimal weightRatio;
    }
}

