package com.swd392.aiviva.exam.dto.response;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamEntryEligibilityResponse {

    private boolean canEnter;
    private String reason;
    private OffsetDateTime opensAt;
    private OffsetDateTime closesAt;
}
