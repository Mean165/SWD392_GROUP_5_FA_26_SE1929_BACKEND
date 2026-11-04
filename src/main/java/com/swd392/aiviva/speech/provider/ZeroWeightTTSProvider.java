package com.swd392.aiviva.speech.provider;

import com.swd392.aiviva.common.exception.ExternalServiceException;
import com.swd392.aiviva.interview.ai.tts.TTSProvider;
import com.swd392.aiviva.interview.ai.tts.TTSStreamInfo;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.tts", name = "provider", havingValue = "zeroweight")
public class ZeroWeightTTSProvider implements TTSProvider {

    private static final Logger log = LoggerFactory.getLogger(ZeroWeightTTSProvider.class);
    private static final int SAMPLE_RATE = 24_000;
    private static final int WAV_HEADER_BYTES = 44;

    private final ZeroWeightStreamingClient streamingClient;
    private final TextToSpeechProperties properties;

    public ZeroWeightTTSProvider(
            ZeroWeightStreamingClient streamingClient,
            TextToSpeechProperties properties) {
        this.streamingClient = streamingClient;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "zeroweight";
    }

    @Override
    public boolean isAvailable() {
        return properties.getZeroWeight().getApiKey() != null
                && !properties.getZeroWeight().getApiKey().isBlank()
                && !properties.getZeroWeight().isInternalNormalizationEnabled();
    }

    @Override
    public String defaultResponseFormat() {
        return "wav";
    }

    @Override
    public TTSStreamInfo streamFormat() {
        return new TTSStreamInfo("application/octet-stream", "pcm_s16le", SAMPLE_RATE, 1, 0);
    }

    @Override
    public String cacheVariant(String language, String responseFormat) {
        var config = properties.getZeroWeight();
        boolean providerNormalize = config.isNormalizeText() && !config.isInternalNormalizationEnabled();
        return String.join("|", providerName(), language == null ? "" : language,
                config.getVoiceId(), Double.toString(config.getSpeed()), Boolean.toString(providerNormalize),
                Integer.toString(SAMPLE_RATE), "wav");
    }

    @Override
    public byte[] synthesize(String text) {
        return synthesize(text, properties.getDefaultLanguage(), "wav");
    }

    @Override
    public byte[] synthesize(String text, String language) {
        return synthesize(text, language, "wav");
    }

    @Override
    public byte[] synthesize(String text, String language, String responseFormat) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        stream(text, language, pcm::writeBytes);
        if (pcm.size() == 0) {
            throw new ExternalServiceException("ZeroWeight returned empty audio.", HttpStatus.BAD_GATEWAY);
        }
        return wrapPcmAsWave(pcm.toByteArray());
    }

    @Override
    public TTSStreamInfo stream(String text, String language, Consumer<byte[]> pcmFrameConsumer) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS text must not be blank.");
        }
        var config = properties.getZeroWeight();
        if (config.isInternalNormalizationEnabled()) {
            throw new ExternalServiceException(
                    "Internal TTS normalization is enabled but no normalizer is configured.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!Double.isFinite(config.getSpeed()) || config.getSpeed() <= 0) {
            throw new IllegalArgumentException("ZeroWeight speed must be a positive finite number.");
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            AtomicLong pcmBytes = new AtomicLong();
            try {
                ZeroWeightPcmResult result = streamingClient.streamPcm(
                        text,
                        "turn-" + UUID.randomUUID(),
                        new ZeroWeightSessionOptions(
                                config.getApiKey(), config.getBaseUrl(), config.getVoiceId(), config.getSpeed(),
                                config.isNormalizeText(), config.getTimeoutMillis(), config.getMaxAudioBytes()),
                        chunk -> {
                            long total = pcmBytes.addAndGet(chunk.length);
                            if (total > config.getMaxAudioBytes()) {
                                throw new ZeroWeightProviderException("TTS_AUDIO_TOO_LARGE", "TTS output exceeded the configured size limit.");
                            }
                            pcmFrameConsumer.accept(chunk);
                        });
                if (pcmBytes.get() == 0) {
                    throw new ZeroWeightProviderException("TTS_EMPTY_AUDIO", "ZeroWeight returned no audio frames.");
                }
                log.info("tts result provider=zeroweight language={} durationMs={} estimatedCredits={} pcmBytes={}",
                        safeLanguage(language), durationMillis(pcmBytes.intValue()), result.estimatedCredits(), pcmBytes.get());
                return new TTSStreamInfo("audio/pcm", "pcm_s16le", SAMPLE_RATE, 1, durationMillis(pcmBytes.intValue()));
            } catch (ZeroWeightProviderException exception) {
                if (attempt < 2 && pcmBytes.get() == 0 && "TTS_TOKEN_EXPIRED".equals(exception.getErrorCode())) {
                    backoff(200);
                    continue;
                }
                if (attempt < 3 && pcmBytes.get() == 0 && "TTS_RATE_LIMITED".equals(exception.getErrorCode())) {
                    backoff(200L * attempt);
                    continue;
                }
                log.warn("tts failure provider=zeroweight code={} language={} attempts={}",
                        exception.getErrorCode(), safeLanguage(language), attempt);
                HttpStatus status = "TTS_CREDITS_EXHAUSTED".equals(exception.getErrorCode())
                        ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
                throw new ExternalServiceException("ZeroWeight TTS provider failed ("
                        + exception.getErrorCode() + ").", status);
            }
        }
        throw new ExternalServiceException("ZeroWeight TTS provider failed.", HttpStatus.BAD_GATEWAY);
    }

    private byte[] wrapPcmAsWave(byte[] pcm) {
        if ((pcm.length & 1) != 0) {
            throw new ZeroWeightProviderException("TTS_BAD_AUDIO_FRAME", "PCM16 output ended on an incomplete sample.");
        }
        ByteBuffer header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        byte[] wave = new byte[header.capacity() + pcm.length];
        System.arraycopy(header.array(), 0, wave, 0, header.capacity());
        System.arraycopy(pcm, 0, wave, header.capacity(), pcm.length);
        return wave;
    }

    private long durationMillis(int pcmBytes) {
        return Math.round((double) pcmBytes / (SAMPLE_RATE * 2) * 1000);
    }

    private String safeLanguage(String language) {
        return "en".equalsIgnoreCase(language) || "en-US".equalsIgnoreCase(language) ? "en" : "vi";
    }

    private void backoff(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("ZeroWeight TTS retry was interrupted.", exception);
        }
    }
}
