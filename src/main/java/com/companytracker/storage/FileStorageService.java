package com.companytracker.storage;

import com.companytracker.config.AppProperties;
import com.companytracker.web.error.AppException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
    );

    private final StoragePort storagePort;
    private final AppProperties properties;

    public FileStorageService(StoragePort storagePort, AppProperties properties) {
        this.storagePort = storagePort;
        this.properties = properties;
    }

    public StoragePort.StoredObject storeUpload(Long ownerId, String idempotencyKey, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("VALIDATION_ERROR", "File is required");
        }
        if (file.getSize() > properties.uploadMaxBytes()) {
            throw AppException.badRequest("VALIDATION_ERROR", "File exceeds max upload size");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extension(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw AppException.badRequest("VALIDATION_ERROR", "Only PDF/DOC/DOCX allowed");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_MIME.contains(contentType) && !contentType.startsWith("application/")) {
            throw AppException.badRequest("VALIDATION_ERROR", "Unsupported content type");
        }
        String keyPart = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey.replaceAll("[^a-zA-Z0-9-_]", "");
        String key = ownerId + "/" + keyPart + "/" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            return storagePort.store(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Upload failed", e);
        }
    }

    public InputStream open(String key) {
        return storagePort.open(key);
    }

    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storagePort.delete(key);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
