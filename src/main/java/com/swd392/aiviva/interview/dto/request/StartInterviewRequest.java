package com.swd392.aiviva.interview.dto.request;

import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Session id is required")
    private UUID sessionId;

    private UUID studentId;
}
