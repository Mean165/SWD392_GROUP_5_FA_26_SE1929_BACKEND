package com.swd392.aiviva.question.infrastructure.storage;

import com.swd392.aiviva.question.storage.port.StoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalStorageAdapter implements StoragePort {
    private final Path root;

    public LocalStorageAdapter(@Value("${question.files.storage-directory:./uploads/question-documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID documentId, String filename, InputStream content) throws IOException {
        Files.createDirectories(root);
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
        Path target = root.resolve(documentId + extension).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid storage path");
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return root.relativize(target).toString();
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid storage path");
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid storage path");
        Files.deleteIfExists(target);
    }
}
