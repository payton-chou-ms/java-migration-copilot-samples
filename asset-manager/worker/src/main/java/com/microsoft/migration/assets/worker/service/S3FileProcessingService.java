package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobServiceClient blobServiceClient;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        blobServiceClient.getBlobContainerClient(containerName)
                .getBlobClient(key)
                .downloadToFile(destination.toString(), true);
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        blobClient.uploadFromFile(source.toString(), true);
        blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));

        // Extract the original key from the thumbnail key
        String originalKey = extractOriginalKey(key);

        // Find and update metadata
        imageMetadataRepository.findAll().stream()
            .filter(metadata -> metadata.getS3Key().equals(originalKey))
            .findFirst()
            .ifPresent(metadata -> {
                metadata.setThumbnailKey(key);
                metadata.setThumbnailUrl(generateUrl(key));
                imageMetadataRepository.save(metadata);
            });
    }

    @Override
    public String getStorageType() {
        return "azure-blob";
    }

    @Override
    protected String generateUrl(String key) {
        return blobServiceClient.getBlobContainerClient(containerName).getBlobClient(key).getBlobUrl();
    }

    private String extractOriginalKey(String key) {
        // For a key like "xxxxx_thumbnail.png", get "xxxxx.png"
        String suffix = "_thumbnail";
        int extensionIndex = key.lastIndexOf('.');
        if (extensionIndex > 0) {
            String nameWithoutExtension = key.substring(0, extensionIndex);
            String extension = key.substring(extensionIndex);

            int suffixIndex = nameWithoutExtension.lastIndexOf(suffix);
            if (suffixIndex > 0) {
                return nameWithoutExtension.substring(0, suffixIndex) + extension;
            }
        }
        return key;
    }
}