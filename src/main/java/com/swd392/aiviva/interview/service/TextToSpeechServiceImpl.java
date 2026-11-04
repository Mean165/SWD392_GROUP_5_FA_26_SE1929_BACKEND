package com.swd392.aiviva.interview.service;

import com.swd392.aiviva.interview.ai.tts.TTSProvider;
import com.swd392.aiviva.interview.ai.tts.TTSStreamInfo;
import com.swd392.aiviva.speech.config.TextToSpeechProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TextToSpeechServiceImpl implements TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechServiceImpl.class);
    private final TTSProvider provider;
    private TextToSpeechProperties properties = new TextToSpeechProperties();
    private final Map<String, byte[]> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final ConcurrentMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();

    public TextToSpeechServiceImpl(TTSProvider provider) {
        this.provider = provider;
    }

    @Autowired
    public void setProperties(TextToSpeechProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] synthesize(String text) {
        String language = properties.getDefaultLanguage();
        String format = effectiveFormat(provider.defaultResponseFormat());
        return cachedSynthesis(text, language, format, () -> provider.synthesize(text));
    }

    @Override
    public byte[] synthesize(String text, String language) {
        String effectiveLanguage = effectiveLanguage(language);
        String format = effectiveFormat(provider.defaultResponseFormat());
        return cachedSynthesis(text, effectiveLanguage, format,
                () -> provider.synthesize(text, effectiveLanguage));
    }

    @Override
    public byte[] synthesize(String text, String language, String responseFormat) {
        String effectiveLanguage = effectiveLanguage(language);
        String effectiveFormat = responseFormat == null || responseFormat.isBlank()
                ? effectiveFormat(provider.defaultResponseFormat()) : responseFormat;
        return cachedSynthesis(text, effectiveLanguage, effectiveFormat,
                () -> provider.synthesize(text, effectiveLanguage, effectiveFormat));
    }

    private byte[] cachedSynthesis(String text, String language, String format,
            java.util.function.Supplier<byte[]> synthesis) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS text must not be blank.");
        }
        String key = cacheKey(text, language, format);
        byte[] cached = cachedAudio(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> shared = inFlight.putIfAbsent(key, mine);
        if (shared != null) {
            return await(shared);
        }
        try {
            byte[] audio = synthesis.get();
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("TTS provider returned empty audio.");
            }
            cacheAudio(key, audio);
            mine.complete(audio.clone());
            return audio.clone();
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private String effectiveLanguage(String language) {
        return language == null || language.isBlank() ? properties.getDefaultLanguage() : language;
    }

    private String effectiveFormat(String format) {
        return format == null || format.isBlank() ? "mp3" : format;
    }

    @Override
    public String cacheKey(String text, String language, String responseFormat) {
        String input = String.join("\u0000", provider.providerName(), text == null ? "" : text,
                language == null ? "" : language, responseFormat == null ? "" : responseFormat,
                provider.cacheVariant(language, responseFormat));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    @Override
    public void prewarm(String text, String language) {
        if (!provider.isAvailable()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                synthesize(text, language);
            } catch (RuntimeException exception) {
                log.warn("TTS prewarm failed: provider={}, errorType={}",
                        provider.providerName(), exception.getClass().getSimpleName());
            }
        });
    }

    @Override
    public TTSStreamInfo stream(String text, String language, Consumer<byte[]> chunkConsumer) {
        return provider.stream(text, language, chunkConsumer);
    }

    @Override
    public TTSStreamInfo streamFormat() {
        return provider.streamFormat();
    }

    private synchronized byte[] cachedAudio(String key) {
        byte[] audio = cache.get(key);
        return audio == null ? null : audio.clone();
    }

    private synchronized void cacheAudio(String key, byte[] audio) {
        cache.put(key, audio.clone());
        int maxEntries = Math.max(1, properties.getCacheMaxEntries());
        while (cache.size() > maxEntries) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private byte[] await(CompletableFuture<byte[]> future) {
        try {
            return future.join().clone();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}
