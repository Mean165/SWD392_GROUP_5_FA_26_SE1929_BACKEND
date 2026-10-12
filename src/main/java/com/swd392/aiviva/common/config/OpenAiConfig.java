package com.swd392.aiviva.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai.api")
@Getter
@Setter
public class OpenAiConfig {

    private String key;
    private String url = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-4o-mini";
    private int timeout = 30000;
}
