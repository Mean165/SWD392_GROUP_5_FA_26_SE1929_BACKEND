package com.swd392.aiviva.interview.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteInterviewRequest {

    @Size(max = 14000000, message = "Full audio payload exceeds the maximum supported size")
    private String fullAudioDataUrl;
}
