package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.StyleVariation;

import java.nio.file.Path;

public interface FileProcessor {
    void downloadOriginal(String key, Path destination) throws Exception;
    void uploadThumbnail(Path source, String key, String contentType) throws Exception;
    void uploadStyleImage(Path source, String key, String contentType,
                          String originalKey, StyleVariation style) throws Exception;
    String getStorageType();
}