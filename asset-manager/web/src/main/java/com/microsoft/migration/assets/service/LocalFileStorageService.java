package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.ImageMetadata;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.ServiceBusConfig.IMAGE_PROCESSING_QUEUE;

@Service
@Profile("dev") // Only active when dev profile is active
public class LocalFileStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Set<String> ALLOWED_EXT = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp")));
    private static final Set<String> ALLOWED_MIME = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp")));

    private final ObjectProvider<ServiceBusSenderClient> senderClientProvider;
    private final ObjectMapper objectMapper;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${local.storage.directory:../storage}")
    private String storageDirectory;

    private Path rootLocation;

    public LocalFileStorageService(ObjectProvider<ServiceBusSenderClient> senderClientProvider, ObjectMapper objectMapper, ImageMetadataRepository imageMetadataRepository) {
        this.senderClientProvider = senderClientProvider;
        this.objectMapper = objectMapper;
        this.imageMetadataRepository = imageMetadataRepository;
    }

    @PostConstruct
    public void init() throws IOException {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        logger.info("Local storage directory: {}", rootLocation);

        // Create directory if it doesn't exist
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            logger.info("Created local storage directory");
        }
    }

    @Override
    public List<S3StorageItem> listObjects() {
        ensureInitialized();
        try {
            return Files.walk(rootLocation, 1)
                .filter(path -> !path.equals(rootLocation))
                .map(path -> {
                    try {
                        String filename = path.getFileName().toString();
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return new S3StorageItem(
                            filename,
                            filename,
                            Files.size(path),
                            attrs.lastModifiedTime().toInstant(),
                            attrs.creationTime().toInstant(),
                            generateUrl(filename)
                        );
                    } catch (IOException e) {
                        logger.error("Failed to read file attributes", e);
                        return null;
                    }
                })
                .filter(s3StorageItem -> s3StorageItem != null)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list files", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        ensureInitialized();
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("Failed to store file with no filename");
        }
        String filename = StringUtils.cleanPath(originalFilename);

        String ext = getExtension(filename).replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        if (!ALLOWED_EXT.contains(ext) ||
            contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IOException("Unsupported file type: " + filename + " (" + contentType + ")");
        }

        // Validate actual image content to guard against spoofed MIME types / extensions
        BufferedImage image;
        try (InputStream imageStream = file.getInputStream()) {
            image = ImageIO.read(imageStream);
        }
        if (image == null) {
            throw new IOException("File does not contain a valid image: " + filename);
        }

        Path targetLocation = resolveSafe(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored file: {}", targetLocation);

        // Send message to queue for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
            filename,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        try {
            ServiceBusSenderClient sender = senderClientProvider.getIfAvailable();
            if (sender != null) {
                sender.sendMessage(new ServiceBusMessage(objectMapper.writeValueAsString(message)));
            } else {
                logger.info("[dev] Service Bus not configured — creating DB record directly for key: {}", message.getKey());
                imageMetadataRepository.findFirstByS3Key(filename).ifPresentOrElse(
                    existing -> logger.debug("[dev] ImageMetadata already exists for key: {}", filename),
                    () -> {
                        ImageMetadata meta = new ImageMetadata();
                        meta.setId(UUID.randomUUID().toString());
                        meta.setFilename(filename);
                        meta.setContentType(file.getContentType());
                        meta.setSize(file.getSize());
                        meta.setS3Key(filename);
                        meta.setS3Url("/storage/view/" + filename);
                        imageMetadataRepository.save(meta);
                        logger.info("[dev] Created ImageMetadata for key: {}", filename);
                    }
                );
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message for key: " + message.getKey(), e);
        }
    }

    private Path resolveSafe(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("Invalid or unsafe key: " + key);
        }
        Path root = rootLocation.toAbsolutePath().normalize();
        Path keyPath;
        try {
            keyPath = Paths.get(key);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid or unsafe key: " + key, e);
        }
        if (keyPath.isAbsolute()) {
            throw new IOException("Invalid or unsafe key: " + key);
        }
        Path resolved = root.resolve(keyPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Invalid or unsafe key: " + key);
        }
        return resolved;
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        ensureInitialized();
        Path file = resolveSafe(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }

    @Override
    public void deleteObject(String key) throws IOException {
        ensureInitialized();
        // Delete both original and thumbnail if it exists
        Path file = resolveSafe(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        Files.delete(file);
        logger.info("Deleted file: {}", file);

        // Try to delete thumbnail if it exists
        try {
            Path thumbnailFile = resolveSafe(getThumbnailKey(key));
            if (Files.exists(thumbnailFile)) {
                Files.delete(thumbnailFile);
                logger.info("Deleted thumbnail file: {}", thumbnailFile);
            }
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist or can't be deleted
            logger.warn("Could not delete thumbnail for {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    private void ensureInitialized() {
        if (rootLocation == null) {
            throw new IllegalStateException("Local storage service has not been initialized");
        }
    }
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
