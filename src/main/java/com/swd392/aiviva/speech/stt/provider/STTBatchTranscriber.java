package com.swd392.aiviva.speech.stt.provider;

import com.swd392.aiviva.speech.stt.domain.STTResult;
import java.util.List;
import java.util.UUID;

public interface STTBatchTranscriber {
    default String providerName() {
        return getClass().getSimpleName();
    }

    STTResult transcribeFull(UUID turnId, byte[] audio, String language, List<String> keyterms);
}
