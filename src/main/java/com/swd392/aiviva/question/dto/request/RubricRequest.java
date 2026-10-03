package com.swd392.aiviva.question.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RubricRequest {

    private String name;
    private String description;
    private Long questionId;
}

