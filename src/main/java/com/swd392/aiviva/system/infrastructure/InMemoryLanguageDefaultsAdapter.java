package com.swd392.aiviva.system.infrastructure;

import com.swd392.aiviva.system.application.port.LanguageDefaultsPersistencePort;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import java.util.Optional;

public class InMemoryLanguageDefaultsAdapter implements LanguageDefaultsPersistencePort {
    private volatile LanguageDefaults defaults;

    @Override
    public Optional<LanguageDefaults> load() {
        return Optional.ofNullable(defaults);
    }

    @Override
    public void save(LanguageDefaults defaults) {
        this.defaults = defaults;
    }
}
