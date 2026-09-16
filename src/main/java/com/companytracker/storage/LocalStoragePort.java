package com.companytracker.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(name = "app.storage-type", havingValue = "local")
public class LocalStoragePort implements StoragePort {

    private final Path root;

    public LocalStoragePort() throws IOException {
        this.root = Path.of(System.getProperty("java.io.tmpdir"), "jobs180-uploads");
        Files.createDirectories(root);
    }

    @Override
    public StoredObject store(String key, InputStream content, long size, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(key, contentType, size);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete file", e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    private Path resolve(String key) {
        if (key.contains("..") || key.startsWith("/") || key.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
