package com.microsoft.migration.assets.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class S3StorageItem {
    private String key;
    private String name;
    private long size;
    private Instant lastModified;
    private Instant uploadedAt;
    private String url;
    private String realisticKey;
    private String cyberpunkKey;
    private String mangaKey;

    public S3StorageItem(String key, String name, long size, Instant lastModified,
                         Instant uploadedAt, String url) {
        this.key = key;
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.uploadedAt = uploadedAt;
        this.url = url;
    }
}