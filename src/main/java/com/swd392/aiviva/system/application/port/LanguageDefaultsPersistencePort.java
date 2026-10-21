package com.swd392.aiviva.system.application.port;

import com.swd392.aiviva.system.domain.LanguageDefaults;
import java.util.Optional;

public interface LanguageDefaultsPersistencePort {
    Optional<LanguageDefaults> load();

    void save(LanguageDefaults defaults);
}
