package com.companytracker.storage;

import java.io.InputStream;

public interface StoragePort {

    StoredObject store(String key, InputStream content, long size, String contentType);

    InputStream open(String key);

    void delete(String key);

    boolean exists(String key);

    record StoredObject(String key, String contentType, long size) {}
}
