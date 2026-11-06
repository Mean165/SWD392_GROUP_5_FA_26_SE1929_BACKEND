package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelExamSessionRequest {
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
