package com.swd392.aiviva.system.application;

import com.swd392.aiviva.system.application.port.LanguageDefaultsPersistencePort;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import com.swd392.aiviva.system.enums.Language;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemLanguageDefaultsService implements SystemLanguageDefaultsProvider {
    private final LanguageDefaultsPersistencePort persistence;
    private volatile LanguageDefaults current;

    public SystemLanguageDefaultsService(
            LanguageDefaultsPersistencePort persistence,
            @Value("${system.language.default-stt:vi}") String defaultStt,
            @Value("${system.language.default-tts:vi}") String defaultTts) {
        this.persistence = persistence;
        LanguageDefaults configured = new LanguageDefaults(parse(defaultStt), parse(defaultTts));
        this.current = persistence.load().orElseGet(() -> {
            persistence.save(configured);
            return configured;
        });
    }

    @Override
    public LanguageDefaults currentDefaults() {
        return current;
    }

    public synchronized LanguageDefaults update(LanguageDefaults defaults) {
        if (defaults == null || defaults.sttLanguage() == null || defaults.ttsLanguage() == null) {
            throw new IllegalArgumentException("STT and TTS languages must be VI or EN");
        }
        persistence.save(defaults);
        current = defaults;
        return current;
    }

    private Language parse(String value) {
        try {
            return Language.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalStateException("System default language must be VI or EN");
        }
    }
}
