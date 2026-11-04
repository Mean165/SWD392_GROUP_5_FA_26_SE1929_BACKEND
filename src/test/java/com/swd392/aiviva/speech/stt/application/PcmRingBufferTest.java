package com.swd392.aiviva.speech.stt.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PcmRingBufferTest {

    @Test
    void snapshotsUncommittedAudioAndBoundsReplayWindow() {
        PcmRingBuffer ring = new PcmRingBuffer(8);
        ring.append(new byte[] {0, 1, 2, 3});
        ring.markCommitted(2);
        ring.append(new byte[] {4, 5, 6, 7});
        assertArrayEquals(new byte[] {2, 3, 4, 5, 6, 7}, ring.snapshotUncommitted().audio());

        ring.append(new byte[] {8, 9, 10, 11});
        assertThrows(PcmRingBuffer.ReplayWindowExceededException.class, ring::snapshotUncommitted);
    }

    @Test
    void rejectsOddPcmAndNonMonotonicCommitOffsets() {
        PcmRingBuffer ring = new PcmRingBuffer(8);
        assertThrows(IllegalArgumentException.class, () -> ring.append(new byte[] {1}));
        ring.append(new byte[] {1, 2, 3, 4});
        ring.markCommitted(2);
        assertThrows(IllegalArgumentException.class, () -> ring.markCommitted(0));
    }
}
