package com.swd392.aiviva.speech.stt.application;

import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** In-process local inference counters; contains no transcript, audio, student, or vendor cost data. */
@Component
public class InferenceResourceTracker {

    private final LongAdder audioMillis = new LongAdder();
    private final LongAdder queueWaitMillis = new LongAdder();
    private final LongAdder inferenceLatencyMillis = new LongAdder();
    private final LongAdder partialEvents = new LongAdder();
    private final LongAdder committedEvents = new LongAdder();
    private final LongAdder failures = new LongAdder();

    public void record(long audioDurationMs, long queueWaitMs, long inferenceLatencyMs,
            long partialCount, long committedCount, boolean failed) {
        audioMillis.add(Math.max(0, audioDurationMs));
        queueWaitMillis.add(Math.max(0, queueWaitMs));
        inferenceLatencyMillis.add(Math.max(0, inferenceLatencyMs));
        partialEvents.add(Math.max(0, partialCount));
        committedEvents.add(Math.max(0, committedCount));
        if (failed) failures.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(audioMillis.sum(), queueWaitMillis.sum(), inferenceLatencyMillis.sum(),
                partialEvents.sum(), committedEvents.sum(), failures.sum());
    }

    public record Snapshot(long audioMillis, long queueWaitMillis, long inferenceLatencyMillis,
            long partialEvents, long committedEvents, long failures) {
    }
}
