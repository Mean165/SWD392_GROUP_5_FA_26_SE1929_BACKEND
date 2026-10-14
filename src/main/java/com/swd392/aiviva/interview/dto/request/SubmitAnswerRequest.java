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
public class SubmitAnswerRequest {

    @NotNull(message = "Attempt id is required")
    private UUID attemptId;

    @NotNull(message = "Turn id is required")
    private UUID turnId;

    /**
     * Optional direct text transcript if audio transcription was done client-side or for testing.
     */
    private String transcriptText;

    /**
     * Audio segment URL or base64 data for STT.
     */
    private String audioSegmentUrl;
}
