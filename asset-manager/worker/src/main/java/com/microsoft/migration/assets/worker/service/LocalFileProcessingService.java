package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.ImageMetadata;
import com.microsoft.migration.assets.worker.model.ImageProcessingMessage;
import com.microsoft.migration.assets.worker.model.StyleVariation;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("dev")
public class LocalFileProcessingService extends AbstractFileProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalFileProcessingService.class);

    @Autowired
    private ImageMetadataRepository imageMetadataRepository;

    @Value("${local.storage.directory:../storage}")
    private String storageDirectory;
    
    private Path rootLocation;
    
    @PostConstruct
    public void init() throws Exception {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        logger.info("Local storage directory: {}", rootLocation);
        
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            logger.info("Created local storage directory");
        }
    }

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        Path sourcePath = rootLocation.resolve(key);
        if (!Files.exists(sourcePath)) {
            throw new java.io.FileNotFoundException("File not found: " + sourcePath);
        }
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void uploadStyleImage(Path source, String key, String contentType,
                                 String originalKey, StyleVariation style) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        imageMetadataRepository.findFirstByS3Key(originalKey).ifPresent(metadata -> {
            style.apply(metadata, key, generateUrl(key));
            imageMetadataRepository.save(metadata);
        });
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    @Override
    protected String generateUrl(String key) {
        return "/storage/view/" + key;
    }

    /** Dev-mode: periodically find images that have no style variations yet and generate them. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void processUnstyledImages() {
        if (!imageGenerationService.isConfigured()) {
            return;
        }
        List<ImageMetadata> pending = imageMetadataRepository.findByRealisticKeyIsNull();
        if (pending.isEmpty()) return;
        logger.info("[dev] Found {} image(s) without style variations", pending.size());
        for (ImageMetadata meta : pending) {
            String key = meta.getS3Key();
            Path originalFile = rootLocation.resolve(key);
            if (!Files.exists(originalFile)) {
                logger.warn("[dev] Original file not found, skipping: {}", key);
                continue;
            }
            logger.info("[dev] Generating style variations for: {}", key);
            ImageProcessingMessage msg = new ImageProcessingMessage(key, "image/png", "local", 0);
            generateStyleVariations(msg, originalFile);
        }
    }
}