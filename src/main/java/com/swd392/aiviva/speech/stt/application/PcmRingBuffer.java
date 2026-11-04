package com.swd392.aiviva.speech.stt.application;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Bounded PCM16 replay window. Offsets are absolute byte offsets in the input stream. */
public final class PcmRingBuffer {

    private final int capacityBytes;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private long totalBytesWritten;
    private long committedThroughOffset;

    public PcmRingBuffer(int capacityBytes) {
        if (capacityBytes < 2) throw new IllegalArgumentException("Ring buffer capacity must be at least one PCM sample.");
        this.capacityBytes = capacityBytes & ~1;
    }

    public synchronized long append(byte[] frame) {
        if (frame == null || (frame.length & 1) != 0) {
            throw new IllegalArgumentException("Ring buffer accepts complete PCM16 samples only.");
        }
        long startOffset = totalBytesWritten;
        totalBytesWritten += frame.length;
        bytes.writeBytes(frame);
        byte[] content = bytes.toByteArray();
        if (content.length > capacityBytes) {
            int remove = (content.length - capacityBytes + 1) & ~1;
            bytes.reset();
            bytes.write(content, remove, content.length - remove);
        }
        return totalBytesWritten;
    }

    public synchronized void markCommitted(long absoluteByteOffset) {
        if (absoluteByteOffset < committedThroughOffset || absoluteByteOffset > totalBytesWritten
                || (absoluteByteOffset & 1) != 0) {
            throw new IllegalArgumentException("Committed offset must be monotonic, in range, and sample-aligned.");
        }
        committedThroughOffset = absoluteByteOffset;
    }

    public synchronized ReplayWindow snapshotUncommitted() {
        return snapshotFrom(committedThroughOffset);
    }

    public synchronized ReplayWindow snapshotUncommittedFrom(long absoluteByteOffset) {
        if (absoluteByteOffset < committedThroughOffset || absoluteByteOffset > totalBytesWritten
                || (absoluteByteOffset & 1) != 0) {
            throw new IllegalArgumentException("Replay offset must be sample-aligned and within the uncommitted range.");
        }
        return snapshotFrom(absoluteByteOffset);
    }

    private ReplayWindow snapshotFrom(long requestedOffset) {
        byte[] content = bytes.toByteArray();
        long retainedStart = totalBytesWritten - content.length;
        if (requestedOffset < retainedStart) {
            throw new ReplayWindowExceededException("Uncommitted PCM exceeded the bounded replay window.");
        }
        int start = Math.toIntExact(requestedOffset - retainedStart);
        return new ReplayWindow(requestedOffset, totalBytesWritten, Arrays.copyOfRange(content, start, content.length));
    }

    public synchronized long totalBytesWritten() {
        return totalBytesWritten;
    }

    public synchronized long committedThroughOffset() {
        return committedThroughOffset;
    }

    public record ReplayWindow(long startOffset, long endOffset, byte[] audio) {
        public ReplayWindow {
            audio = audio.clone();
        }

        @Override
        public byte[] audio() {
            return audio.clone();
        }
    }

    public static final class ReplayWindowExceededException extends RuntimeException {
        public ReplayWindowExceededException(String message) {
            super(message);
        }
    }
}
