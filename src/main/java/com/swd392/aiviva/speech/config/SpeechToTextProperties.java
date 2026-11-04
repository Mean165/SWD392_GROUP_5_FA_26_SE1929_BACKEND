package com.swd392.aiviva.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech.stt")
public class SpeechToTextProperties {

    private String provider = "google";
    private String allowedOrigins = "";
    private final Google google = new Google();
    private final Session session = new Session();
    private final DeviceCheck deviceCheck = new DeviceCheck();
    private final Mock mock = new Mock();
    private final ElevenLabs elevenlabs = new ElevenLabs();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Google getGoogle() { return google; }
    public Session getSession() { return session; }
    public DeviceCheck getDeviceCheck() { return deviceCheck; }
    public Mock getMock() { return mock; }
    public ElevenLabs getElevenlabs() { return elevenlabs; }

    public static class Google {
        private String projectId = "";
        private String location = "eu";
        private String model = "chirp_3";

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Session {
        private int maxDurationSeconds = 60;
        private int maxFrameBytes = 24000;
        private int finishTimeoutSeconds = 10;

        public int getMaxDurationSeconds() { return maxDurationSeconds; }
        public void setMaxDurationSeconds(int maxDurationSeconds) { this.maxDurationSeconds = maxDurationSeconds; }
        public int getMaxFrameBytes() { return maxFrameBytes; }
        public void setMaxFrameBytes(int maxFrameBytes) { this.maxFrameBytes = maxFrameBytes; }
        public int getFinishTimeoutSeconds() { return finishTimeoutSeconds; }
        public void setFinishTimeoutSeconds(int finishTimeoutSeconds) { this.finishTimeoutSeconds = finishTimeoutSeconds; }
    }

    public static class DeviceCheck {
        private int sessionsPerMinute = 5;

        public int getSessionsPerMinute() { return sessionsPerMinute; }
        public void setSessionsPerMinute(int sessionsPerMinute) { this.sessionsPerMinute = sessionsPerMinute; }
    }

    public static class Mock {
        private String scenario = "normal";
        private String scenarioFile = "";

        public String getScenario() { return scenario; }
        public void setScenario(String scenario) { this.scenario = scenario; }
        public String getScenarioFile() { return scenarioFile; }
        public void setScenarioFile(String scenarioFile) { this.scenarioFile = scenarioFile; }
    }

    public static class ElevenLabs {
        private String apiKey = "";
        private String region = "global";
        private boolean enableLogging = false;
        private int connectTimeoutSeconds = 10;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { region = value; }
        public boolean isEnableLogging() { return enableLogging; }
        public void setEnableLogging(boolean value) { enableLogging = value; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }

        public String realtimeHost() {
            return switch (region == null ? "global" : region.toLowerCase(java.util.Locale.ROOT)) {
                case "global" -> "api.elevenlabs.io";
                case "us" -> "api.us.elevenlabs.io";
                case "eu" -> "api.eu.residency.elevenlabs.io";
                case "in" -> "api.in.residency.elevenlabs.io";
                case "sg" -> "api.sg.residency.elevenlabs.io";
                default -> throw new IllegalArgumentException("Unsupported ElevenLabs region.");
            };
        }
    }
}
