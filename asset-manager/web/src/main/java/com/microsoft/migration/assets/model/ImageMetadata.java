package com.microsoft.migration.assets.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class ImageMetadata {
    @Id
    private String id;
    private String filename;
    private String contentType;
    private Long size;
    private String s3Key;
    private String s3Url;
    private String thumbnailKey;
    private String thumbnailUrl;
    private String realisticKey;
    private String realisticUrl;
    private String cyberpunkKey;
    private String cyberpunkUrl;
    private String mangaKey;
    private String mangaUrl;
    private LocalDateTime uploadedAt;
    private LocalDateTime lastModified;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastModified = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}