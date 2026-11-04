package com.swd392.aiviva.interview.ai.tts;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.tts", name = "provider", havingValue = "mock")
public class MockTTSProvider implements TTSProvider {

    private static final int RATE = 24_000;

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public byte[] synthesize(String text) {
        return synthesize(text, "vi", "wav");
    }

    @Override
    public byte[] synthesize(String text, String language) {
        return synthesize(text, language, "wav");
    }

    @Override
    public byte[] synthesize(String text, String language, String responseFormat) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS input text is required.");
        }
        int samples = RATE / 4;
        ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double envelope = Math.min(1.0, Math.min(i / 240.0, (samples - i) / 240.0));
            pcm.putShort((short) (Math.sin(2.0 * Math.PI * 660.0 * i / RATE) * 1200 * envelope));
        }
        return wav(pcm.array());
    }

    @Override
    public String cacheVariant(String language, String responseFormat) {
        return "mock|" + language + "|wav";
    }

    @Override
    public TTSStreamInfo streamFormat() {
        return new TTSStreamInfo("audio/wav", "encoded-audio", 24_000, 1, 250);
    }

    private byte[] wav(byte[] pcm) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
            DataOutputStream data = new DataOutputStream(out);
            data.writeBytes("RIFF");
            writeLe32(data, 36 + pcm.length);
            data.writeBytes("WAVEfmt ");
            writeLe32(data, 16);
            writeLe16(data, 1);
            writeLe16(data, 1);
            writeLe32(data, RATE);
            writeLe32(data, RATE * 2);
            writeLe16(data, 2);
            writeLe16(data, 16);
            data.writeBytes("data");
            writeLe32(data, pcm.length);
            data.write(pcm);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to build mock WAV audio.", impossible);
        }
    }

    private void writeLe16(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
    }

    private void writeLe32(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
        out.writeByte((value >>> 16) & 0xff);
        out.writeByte((value >>> 24) & 0xff);
    }
}
