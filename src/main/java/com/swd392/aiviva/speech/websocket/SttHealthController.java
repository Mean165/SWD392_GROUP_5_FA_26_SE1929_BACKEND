package com.swd392.aiviva.speech.websocket;

import com.swd392.aiviva.interview.ai.stt.SpeechToTextProvider;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/device-check/stt")
public class SttHealthController {

    private final SpeechToTextProvider provider;
    private final SpeechToTextProperties properties;

    public SttHealthController(SpeechToTextProvider provider, SpeechToTextProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean ready = provider.isAvailable();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ready", ready);
        payload.put("provider", provider.providerName());
        payload.put("languages", List.of("vi-VN", "en-US"));
        payload.put("sampleRate", 16000);
        payload.put("encoding", "pcm_s16le");
        payload.put("maxDurationSeconds", properties.getSession().getMaxDurationSeconds());
        if (!ready) payload.put("errorCode", provider.readinessErrorCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payload);
    }
}
