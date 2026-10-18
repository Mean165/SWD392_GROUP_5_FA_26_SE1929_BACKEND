package com.swd392.aiviva.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "interview")
@Validated
@Getter
@Setter
public class InterviewConfig {

    @NotBlank
    private String language = "vi";
    @Min(1)
    private int answerTimeLimitSeconds = 180;
    @Min(1)
    private int maxAudioBytes = 10 * 1024 * 1024;
    @Min(1)
    private int maxTranscriptCharacters = 20000;

    private String audioStorageDirectory = "./uploads/audio";
}
