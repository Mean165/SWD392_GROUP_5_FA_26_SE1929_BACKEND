package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignStudentSessionRequest {

    private UUID sessionId;

    @NotBlank(message = "Student code is required")
    private String studentCode;

    private OffsetDateTime scheduledTime;

    private String status;
}
