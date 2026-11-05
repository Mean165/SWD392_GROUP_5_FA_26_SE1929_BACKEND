package com.swd392.aiviva.exam.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class UpdateAssignmentScheduleRequest {
    @NotNull(message = "scheduledTime is required")
    private OffsetDateTime scheduledTime;
    private Integer attemptNo = 1;
}
