package com.swd392.aiviva.speech.provider;

import java.util.function.Consumer;

public interface ZeroWeightStreamingClient {

    ZeroWeightPcmResult streamPcm(
            String text,
            String contextId,
            ZeroWeightSessionOptions options,
            Consumer<byte[]> pcmFrameConsumer);
}
