package com.swd392.aiviva.system.domain;

import com.swd392.aiviva.system.enums.Language;

public record LanguageDefaults(Language sttLanguage, Language ttsLanguage) {
    public LanguageDefaults {
        if (sttLanguage == null || ttsLanguage == null) {
            throw new IllegalArgumentException("STT and TTS languages are required");
        }
    }
}
