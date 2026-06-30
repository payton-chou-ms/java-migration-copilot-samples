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
import java.util.UUID;
import java.util.stream.Stream;
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
        autoRegisterLocalImages();
        List<ImageMetadata> pending = imageMetadataRepository.findByRealisticKeyIsNull();
        if (pending.isEmpty()) return;
        logger.info("[dev] Found {} image(s) without style variations", pending.size());
        for (ImageMetadata meta : pending) {
            String key = meta.getS3Key();
            // Skip style-derived files that were registered before the filter was in place
            if (isStyleDerivedKey(key)) {
                logger.debug("[dev] Removing spurious record for style-derived file: {}", key);
                imageMetadataRepository.delete(meta);
                continue;
            }
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

    private static boolean isStyleDerivedKey(String key) {
        if (key == null) return false;
        int dot = key.lastIndexOf('.');
        String stem = dot > 0 ? key.substring(0, dot) : key;
        return stem.endsWith("_realistic") || stem.endsWith("_cyberpunk") || stem.endsWith("_manga");
    }

    /** Scan the local storage directory and create ImageMetadata records for any unregistered images. */
    private void autoRegisterLocalImages() {
        if (rootLocation == null || !Files.exists(rootLocation)) return;
        try (Stream<Path> files = Files.list(rootLocation)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> {
                     String name = path.getFileName().toString();
                     int dot = name.lastIndexOf('.');
                     String stem = dot > 0 ? name.substring(0, dot) : name;
                     // Skip files that are already generated style outputs
                     return !stem.endsWith("_realistic") && !stem.endsWith("_cyberpunk") && !stem.endsWith("_manga");
                 })
                 .forEach(path -> {
                     String key = path.getFileName().toString();
                     if (imageMetadataRepository.findFirstByS3Key(key).isEmpty()) {
                         ImageMetadata meta = new ImageMetadata();
                         meta.setId(UUID.randomUUID().toString());
                         meta.setFilename(key);
                         meta.setS3Key(key);
                         meta.setS3Url(generateUrl(key));
                         imageMetadataRepository.save(meta);
                         logger.info("[dev] Auto-registered image: {}", key);
                     }
                 });
        } catch (Exception e) {
            logger.warn("[dev] Failed to scan storage directory: {}", e.getMessage());
        }
    }
}