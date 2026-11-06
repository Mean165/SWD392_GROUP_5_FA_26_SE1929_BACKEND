package com.swd392.aiviva.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.stt")
public class SpeechToTextProperties {

    private String provider = "local";
    private String allowedOrigins = "";
    private final Session session = new Session();
    private final DeviceCheck deviceCheck = new DeviceCheck();
    private final Mock mock = new Mock();

    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String value) { allowedOrigins = value; }
    public Session getSession() { return session; }
    public DeviceCheck getDeviceCheck() { return deviceCheck; }
    public Mock getMock() { return mock; }

    public static class Session {
        private int maxDurationSeconds = 60;
        private int maxFrameBytes = 24_000;
        private int finishTimeoutSeconds = 10;

        public int getMaxDurationSeconds() { return maxDurationSeconds; }
        public void setMaxDurationSeconds(int value) { maxDurationSeconds = value; }
        public int getMaxFrameBytes() { return maxFrameBytes; }
        public void setMaxFrameBytes(int value) { maxFrameBytes = value; }
        public int getFinishTimeoutSeconds() { return finishTimeoutSeconds; }
        public void setFinishTimeoutSeconds(int value) { finishTimeoutSeconds = value; }
    }

    public static class DeviceCheck {
        private int sessionsPerMinute = 5;

        public int getSessionsPerMinute() { return sessionsPerMinute; }
        public void setSessionsPerMinute(int value) { sessionsPerMinute = value; }
    }

    public static class Mock {
        private String scenario = "normal";
        private String scenarioFile = "";

        public String getScenario() { return scenario; }
        public void setScenario(String value) { scenario = value; }
        public String getScenarioFile() { return scenarioFile; }
        public void setScenarioFile(String value) { scenarioFile = value; }
    }
}
