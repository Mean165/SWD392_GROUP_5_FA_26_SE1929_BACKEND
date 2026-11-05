package com.swd392.aiviva.interview.dto.request;

import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartInterviewRequest {

    private UUID sessionId;

    private UUID assignmentId;

    private UUID examAttemptId;

    @AssertTrue(message = "assignmentId or sessionId is required")
    public boolean isAssignmentOrSessionProvided() {
        return examAttemptId != null || assignmentId != null || sessionId != null;
    }

}
