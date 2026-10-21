package com.swd392.aiviva.system.dto.request;

import com.swd392.aiviva.system.enums.Language;
import jakarta.validation.constraints.NotNull;

public record UpdateLanguageDefaultsRequest(
        @NotNull(message = "STT language is required") Language sttLanguage,
        @NotNull(message = "TTS language is required") Language ttsLanguage) {
}
