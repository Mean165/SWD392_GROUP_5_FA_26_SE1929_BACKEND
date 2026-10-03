package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamRequest {

    @NotBlank(message = "Exam title is required")
    private String title;

    @NotBlank(message = "Subject code is required")
    private String subjectCode;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    @NotNull(message = "Duration is required")
    private Integer durationMinutes;

    @NotNull(message = "Total questions is required")
    private Integer totalQuestions;
}

