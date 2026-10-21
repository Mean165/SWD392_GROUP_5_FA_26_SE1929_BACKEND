package com.swd392.aiviva.system.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import com.swd392.aiviva.system.enums.Language;
import com.swd392.aiviva.system.infrastructure.JsonFileLanguageDefaultsAdapter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemLanguageDefaultsServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void defaultsSurviveRecreatingTheApplicationService() {
        String path = temporaryDirectory.resolve("language-defaults.json").toString();
        JsonFileLanguageDefaultsAdapter firstStore = new JsonFileLanguageDefaultsAdapter(new ObjectMapper(), path);
        SystemLanguageDefaultsService firstService = new SystemLanguageDefaultsService(firstStore, "vi", "vi");
        LanguageDefaults expected = new LanguageDefaults(Language.EN, Language.VI);

        assertEquals(expected, firstService.update(expected));

        JsonFileLanguageDefaultsAdapter restartedStore = new JsonFileLanguageDefaultsAdapter(new ObjectMapper(), path);
        SystemLanguageDefaultsService restartedService = new SystemLanguageDefaultsService(restartedStore, "vi", "vi");
        assertEquals(expected, restartedService.currentDefaults());
    }
}
