package com.chubb.claims.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores each document as {root}/{claimId}/{key}. Keys are server-generated UUIDs and never derived from the
 * uploaded file name, so a client cannot influence the path (no traversal).
 */
@Component
public class LocalFileDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalFileDocumentStorage(
            @Value("${app.documents.dir:${java.io.tmpdir}/claims-documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void store(UUID claimId, String key, byte[] content) {
        try {
            Path target = resolve(claimId, key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store document", e);
        }
    }

    @Override
    public byte[] load(UUID claimId, String key) {
        try {
            return Files.readAllBytes(resolve(claimId, key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read document", e);
        }
    }

    @Override
    public void delete(UUID claimId, String key) {
        try {
            Files.deleteIfExists(resolve(claimId, key));
        } catch (IOException e) {
            // best effort clean-up
        }
    }

    private Path resolve(UUID claimId, String key) {
        Path path = root.resolve(claimId.toString()).resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
