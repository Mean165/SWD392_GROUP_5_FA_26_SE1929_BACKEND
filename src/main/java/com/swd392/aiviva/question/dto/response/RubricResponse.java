package com.swd392.aiviva.question.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RubricResponse {

    private Long id;
    private String name;
    private String description;
    private Long questionId;
}

