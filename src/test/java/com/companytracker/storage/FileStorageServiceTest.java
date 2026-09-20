package com.companytracker.storage;

import com.companytracker.config.AppProperties;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock StoragePort storagePort;
    FileStorageService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "local", 100, 3600, true, "", "UTC",
                new AppProperties.S3("http://x", "b", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(5, 3, 60, 10, 120)
        );
        service = new FileStorageService(storagePort, props);
    }

    @Test
    void storesAllowedPdf() {
        when(storagePort.store(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoragePort.StoredObject(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});
        StoragePort.StoredObject stored = service.storeUpload(1L, "idem-1", file);
        assertTrue(stored.key().startsWith("1/"));
        assertTrue(stored.key().endsWith(".pdf"));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        AppException ex = assertThrows(AppException.class, () -> service.storeUpload(1L, "k", file));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[200]);
        AppException ex = assertThrows(AppException.class, () -> service.storeUpload(1L, "k", file));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void rejectsExeExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{1});
        AppException ex = assertThrows(AppException.class, () -> service.storeUpload(1L, "k", file));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void deleteQuietlySwallowsErrors() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(storagePort).delete("k");
        service.deleteQuietly("k");
        verify(storagePort).delete("k");
    }

    @Test
    void localStorageRejectsPathTraversal() throws Exception {
        LocalStoragePort local = new LocalStoragePort();
        assertThrows(IllegalArgumentException.class,
                () -> local.store("../secret", new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf"));
    }
}
