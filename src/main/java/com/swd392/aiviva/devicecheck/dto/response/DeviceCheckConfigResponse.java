package com.swd392.aiviva.devicecheck.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCheckConfigResponse {

    private List<String> languages;
    private String defaultLanguage;
    private List<String> acceptedMimeTypes;
    private int maxAudioBytes;
    private int maxDurationSeconds;
    private int recommendedRecordingSeconds;
    private Map<String, String> sampleSentences;
}
