package com.swd392.aiviva.speech.provider;

import com.swd392.aiviva.interview.ai.tts.TTSProvider;
import com.swd392.aiviva.interview.ai.tts.TTSStreamInfo;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "speech.tts", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalTextToSpeechProvider implements TTSProvider {

    private final LocalInferenceClient inferenceClient;
    private final TextToSpeechProperties properties;

    public LocalTextToSpeechProvider(LocalInferenceClient inferenceClient, TextToSpeechProperties properties) {
        this.inferenceClient = inferenceClient;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "local-piper";
    }

    @Override
    public boolean isAvailable() {
        if (!inferenceClient.isConfigured()) return false;
        try {
            return inferenceClient.health().path("tts").path("ready").asBoolean(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String defaultResponseFormat() {
        return "wav";
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
        validate(text, language);
        String normalizedLanguage = languageCode(language);
        String format = responseFormat == null || responseFormat.isBlank() ? "wav" : responseFormat.toLowerCase(java.util.Locale.ROOT);
        if (!"wav".equals(format) && !"pcm".equals(format)) {
            throw new IllegalArgumentException("Local Piper TTS supports WAV or PCM output.");
        }
        return inferenceClient.synthesize(text, normalizedLanguage, voice(normalizedLanguage),
                properties.getLocal().getSpeed(), format);
    }

    @Override
    public TTSStreamInfo stream(String text, String language, java.util.function.Consumer<byte[]> chunkConsumer) {
        validate(text, language);
        String normalizedLanguage = languageCode(language);
        return inferenceClient.streamSynthesis(text, normalizedLanguage, voice(normalizedLanguage),
                properties.getLocal().getSpeed(), chunkConsumer);
    }

    @Override
    public TTSStreamInfo streamFormat() {
        int sampleRate = "korva".equalsIgnoreCase(properties.getLocal().getEngine()) ? 44_100 : 22_050;
        return new TTSStreamInfo("audio/wav", "wav", sampleRate, 1, 0);
    }

    @Override
    public String cacheVariant(String language, String responseFormat) {
        String code = languageCode(language == null || language.isBlank()
                ? properties.getDefaultLanguage() : language);
        return String.join("|", providerName(), properties.getLocal().getEngine(), voice(code),
                Double.toString(properties.getLocal().getSpeed()), responseFormat == null ? "wav" : responseFormat);
    }

    private void validate(String text, String language) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("TTS text is required.");
        if (text.length() > properties.getLocal().getMaxTextLength()) {
            throw new IllegalArgumentException("TTS text exceeds the configured maximum length.");
        }
        languageCode(language);
    }

    private String languageCode(String language) {
        String value = language == null || language.isBlank() ? properties.getDefaultLanguage() : language;
        if ("vi".equalsIgnoreCase(value) || "vi-VN".equalsIgnoreCase(value)) return "vi";
        if ("en".equalsIgnoreCase(value) || "en-US".equalsIgnoreCase(value)) return "en";
        throw new IllegalArgumentException("Local TTS language must be vi or en.");
    }

    private String voice(String language) {
        return "vi".equals(language) ? properties.getLocal().getVoiceVi() : properties.getLocal().getVoiceEn();
    }
}
