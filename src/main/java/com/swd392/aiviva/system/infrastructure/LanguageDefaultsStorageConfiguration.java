package com.swd392.aiviva.system.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.system.application.port.LanguageDefaultsPersistencePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LanguageDefaultsStorageConfiguration {
    @Bean
    @ConditionalOnProperty(name = "system.language.config-file")
    public LanguageDefaultsPersistencePort languageDefaultsFileStore(ObjectMapper mapper,
            @Value("${system.language.config-file}") String file) {
        return new JsonFileLanguageDefaultsAdapter(mapper, file);
    }

    @Bean
    @ConditionalOnMissingBean(LanguageDefaultsPersistencePort.class)
    public LanguageDefaultsPersistencePort languageDefaultsInMemoryStore() {
        return new InMemoryLanguageDefaultsAdapter();
    }
}
