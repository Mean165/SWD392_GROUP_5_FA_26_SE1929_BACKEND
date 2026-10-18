package com.swd392.aiviva.devicecheck.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCheckSttResponse {

    private String transcript;
    private double matchScore;
    private String level;
    private long durationMs;
    private String language;
    private String suggestion;
}
