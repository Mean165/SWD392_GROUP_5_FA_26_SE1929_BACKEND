package com.swd392.aiviva.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "local.inference")
public class LocalInferenceProperties {

    private String url = "http://127.0.0.1:8000";
    private String token = "";
    private int connectTimeoutMs = 2_000;
    private int requestTimeoutSeconds = 180;

    public String getUrl() { return url; }
    public void setUrl(String value) { url = value; }
    public String getToken() { return token; }
    public void setToken(String value) { token = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int value) { requestTimeoutSeconds = value; }
}
