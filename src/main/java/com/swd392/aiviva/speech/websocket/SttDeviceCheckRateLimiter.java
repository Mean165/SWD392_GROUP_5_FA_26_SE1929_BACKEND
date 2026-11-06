package com.swd392.aiviva.speech.websocket;

import com.swd392.aiviva.speech.config.SpeechToTextProperties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SttDeviceCheckRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> activeStudents = ConcurrentHashMap.newKeySet();
    private final SpeechToTextProperties properties;
    private final AtomicInteger operations = new AtomicInteger();

    public SttDeviceCheckRateLimiter(SpeechToTextProperties properties) {
        this.properties = properties;
    }

    public boolean allow(UUID studentId) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(studentId, (id, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_MILLIS) {
                return new Window(now);
            }
            return current;
        });
        boolean allowed = window.count.incrementAndGet() <= properties.getDeviceCheck().getSessionsPerMinute();
        if (operations.incrementAndGet() % 128 == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS * 2);
        }
        return allowed;
    }

    public boolean tryStart(UUID studentId) {
        return allow(studentId) && activeStudents.add(studentId);
    }

    public void finish(UUID studentId) {
        activeStudents.remove(studentId);
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
