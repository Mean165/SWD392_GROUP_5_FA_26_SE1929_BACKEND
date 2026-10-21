package com.swd392.aiviva.question.storage.port;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface StoragePort {
    String store(UUID documentId, String filename, InputStream content) throws IOException;
    InputStream open(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
}
