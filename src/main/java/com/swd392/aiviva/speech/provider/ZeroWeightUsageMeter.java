package com.swd392.aiviva.speech.provider;

import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeroWeightUsageMeter {

    private static final Logger log = LoggerFactory.getLogger(ZeroWeightUsageMeter.class);
    private final TextToSpeechProperties properties;
    private final AtomicLong estimatedCredits = new AtomicLong();
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    public ZeroWeightUsageMeter(TextToSpeechProperties properties) {
        this.properties = properties;
    }

    public long recordSession(long socketOpenMillis, String gateway) {
        long credits = Math.max(1, (socketOpenMillis + 999) / 1000);
        long total = estimatedCredits.addAndGet(credits);
        long budget = properties.getZeroWeight().getCreditBudget();
        long remaining = budget > 0 ? Math.max(0, budget - total) : -1;
        String safeGateway = gateway == null ? "unknown" : gateway.replaceAll("[^A-Za-z0-9._-]", "_");
        log.info("tts provider=zeroweight gateway={} sessionMillis={} estimatedCredits={} processEstimatedCredits={}",
                safeGateway, socketOpenMillis, credits, total);
        long threshold = properties.getZeroWeight().getCreditWarningRemaining();
        if (budget > 0 && remaining <= threshold && warningLogged.compareAndSet(false, true)) {
            log.warn("ZeroWeight estimated remaining credits are near configured threshold: remaining={} threshold={}",
                    remaining, threshold);
        }
        return credits;
    }

    public long getEstimatedCredits() {
        return estimatedCredits.get();
    }
}
