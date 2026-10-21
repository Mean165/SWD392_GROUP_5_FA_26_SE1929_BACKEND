package com.swd392.aiviva.system.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.system.application.port.LanguageDefaultsPersistencePort;
import com.swd392.aiviva.system.domain.LanguageDefaults;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class JsonFileLanguageDefaultsAdapter implements LanguageDefaultsPersistencePort {
    private final ObjectMapper mapper;
    private final Path file;

    public JsonFileLanguageDefaultsAdapter(ObjectMapper mapper, String file) {
        this.mapper = mapper;
        this.file = Path.of(file).toAbsolutePath().normalize();
    }

    @Override
    public synchronized Optional<LanguageDefaults> load() {
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file.toFile(), LanguageDefaults.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read persisted system language defaults", e);
        }
    }

    @Override
    public synchronized void save(LanguageDefaults defaults) {
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "language-defaults-", ".tmp");
            mapper.writeValue(temporary.toFile(), defaults);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist system language defaults", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            }
        }
    }
}
