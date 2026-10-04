package com.swd392.aiviva.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartInterviewRequest {

    @NotNull(message = "Exam id is required")
    private Long examId;

    @NotNull(message = "Student id is required")
    private Long studentId;
}

