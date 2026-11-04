package com.swd392.aiviva.speech.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.interview.ai.tts.TTSProvider;
import com.swd392.aiviva.speech.provider.LocalInferenceClient;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/device-check/stt")
public class SttHealthController {

    private final SpeechToTextProvider sttProvider;
    private final TTSProvider ttsProvider;
    private final LocalInferenceClient inferenceClient;
    private final SpeechToTextProperties properties;

    public SttHealthController(SpeechToTextProvider sttProvider, TTSProvider ttsProvider,
            LocalInferenceClient inferenceClient, SpeechToTextProperties properties) {
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
        this.inferenceClient = inferenceClient;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        JsonNode runtime = null;
        if (inferenceClient.isConfigured() && !"mock".equals(sttProvider.providerName())) {
            try {
                runtime = inferenceClient.health();
            } catch (RuntimeException ignored) {
                // A missing local runtime is represented as not-ready, not as an unhelpful HTTP 500.
            }
        }
        Map<String, Object> stt = providerHealth(runtime == null ? null : runtime.path("stt"), sttProvider);
        Map<String, Object> tts = providerHealth(runtime == null ? null : runtime.path("tts"), ttsProvider);
        Map<String, Object> resources = runtime != null && runtime.path("resources").isObject()
                ? objectFields(runtime.path("resources")) : Map.of();
        Map<String, Object> versions = runtime != null && runtime.path("versions").isObject()
                ? objectFields(runtime.path("versions")) : Map.of();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stt", stt);
        payload.put("tts", tts);
        payload.put("resources", resources);
        payload.put("versions", versions);
        payload.put("sampleRate", 16_000);
        payload.put("encoding", "pcm_s16le");
        payload.put("maxDurationSeconds", properties.getSession().getMaxDurationSeconds());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payload);
    }

    private Map<String, Object> providerHealth(JsonNode service, Object provider) {
        boolean mock = provider instanceof SpeechToTextProvider stt && "mock".equals(stt.providerName())
                || provider instanceof TTSProvider tts && "mock".equals(tts.providerName());
        boolean ready = mock ? true : service != null && service.path("ready").asBoolean(false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", ready);
        result.put("provider", mock ? "mock" : "local");
        result.put("engine", service == null ? (mock ? "mock" : "local-inference") : service.path("engine").asText("local"));
        result.put("model", service == null ? "" : service.path("model").asText(""));
        result.put("device", service == null ? (mock ? "cpu" : "unknown") : service.path("device").asText("unknown"));
        if (!ready) {
            String reason = service == null ? "Local inference service is not running or internal authentication is not configured."
                    : service.path("reason").asText("Local speech model is not ready.");
            result.put("reason", reason);
        } else {
            result.put("reason", service == null ? "" : service.path("reason").asText(""));
            if (service != null && service.has("loaded_at")) result.put("loaded_at", service.path("loaded_at").asText());
            if (service != null && service.has("load_ms")) result.put("load_ms", service.path("load_ms").asLong());
            if (service != null && service.has("compute_type")) result.put("compute_type", service.path("compute_type").asText());
        }
        result.put("languages", List.of("vi-VN", "en-US"));
        return result;
    }

    private Map<String, Object> objectFields(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), objectMapperValue(entry.getValue())));
        return result;
    }

    private Object objectMapperValue(JsonNode value) {
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return value.numberValue();
        if (value.isTextual()) return value.asText();
        if (value.isNull()) return null;
        return value.toString();
    }
}
