package com.swd392.aiviva.speech.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.swd392.aiviva.speech.provider.LocalInferenceClient;
import com.swd392.aiviva.speech.stt.application.InferenceResourceTracker;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/admin/usage/stt-tts")
public class SttTtsUsageController {

    private final InferenceResourceTracker tracker;
    private final LocalInferenceClient localInferenceClient;

    public SttTtsUsageController(InferenceResourceTracker tracker, LocalInferenceClient localInferenceClient) {
        this.tracker = tracker;
        this.localInferenceClient = localInferenceClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> usage() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scope", "process_since_startup");
        response.put("counters", tracker.snapshot());
        response.put("resources", localResources());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private Map<String, Object> localResources() {
        if (!localInferenceClient.isConfigured()) return Map.of("available", false, "reason", "not_configured");
        try {
            JsonNode resources = localInferenceClient.health().path("resources");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("available", true);
            resources.fields().forEachRemaining(entry -> snapshot.put(entry.getKey(),
                    entry.getValue().isNumber() ? entry.getValue().numberValue() : entry.getValue().asText()));
            return snapshot;
        } catch (RuntimeException exception) {
            return Map.of("available", false, "reason", "local_inference_unavailable");
        }
    }
}
