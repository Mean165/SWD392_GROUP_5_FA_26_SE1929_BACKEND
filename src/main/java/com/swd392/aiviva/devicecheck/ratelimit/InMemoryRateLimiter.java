package com.swd392.aiviva.devicecheck.ratelimit;

import com.swd392.aiviva.common.exception.RateLimitExceededException;
import com.swd392.aiviva.devicecheck.config.DeviceCheckProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class InMemoryRateLimiter {

    // In-memory limits are shared only within one application instance.
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<UUID, ArrayDeque<Instant>> requests = new ConcurrentHashMap<>();
    private final int requestLimit;

    public InMemoryRateLimiter(DeviceCheckProperties properties) {
        requestLimit = properties.getRateLimit().getRequestsPerMinute();
    }

    public void check(UUID userId) {
        Instant now = Instant.now();
        AtomicLong retryAfter = new AtomicLong();
        requests.compute(userId, (key, bucket) -> {
            ArrayDeque<Instant> timestamps = bucket == null ? new ArrayDeque<>() : bucket;
            synchronized (timestamps) {
                removeExpired(timestamps, now);
                if (timestamps.size() >= requestLimit) {
                    retryAfter.set(Math.max(1, Duration.between(now, timestamps.peekFirst().plus(WINDOW)).toSeconds() + 1));
                    return timestamps;
                }
                timestamps.addLast(now);
                return timestamps;
            }
        });
        if (retryAfter.get() > 0) {
            throw new RateLimitExceededException(retryAfter.get());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void removeExpiredBuckets() {
        Instant now = Instant.now();
        requests.forEach((userId, bucket) -> requests.computeIfPresent(userId, (key, current) -> {
            synchronized (current) {
                removeExpired(current, now);
                return current.isEmpty() ? null : current;
            }
        }));
    }

    private void removeExpired(ArrayDeque<Instant> timestamps, Instant now) {
        Instant threshold = now.minus(WINDOW);
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(threshold)) {
            timestamps.removeFirst();
        }
    }
}
