package com.swd392.aiviva.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
    @Size(max = 20000, message = "Transcript exceeds the maximum supported length")
    private String transcriptText;

    /**
     * Base64 data URL for a single audio file; external URLs are not accepted or fetched.
     */
    @Size(max = 14000000, message = "Audio payload exceeds the maximum supported size")
    private String audioSegmentUrl;

    private String language;

    @Min(value = 1, message = "Audio duration must be at least one second")
    private Integer audioDurationSeconds;
}
