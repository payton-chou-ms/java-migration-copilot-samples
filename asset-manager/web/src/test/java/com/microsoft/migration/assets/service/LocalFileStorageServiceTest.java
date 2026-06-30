package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.ImageProcessingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static com.microsoft.migration.assets.config.RabbitConfig.IMAGE_PROCESSING_QUEUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    );

    @Mock
    private RabbitTemplate rabbitTemplate;

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new LocalFileStorageService(rabbitTemplate);
        ReflectionTestUtils.setField(service, "storageDirectory", tempDir.toString());
        service.init();
    }

    // Path safety tests
    @Test
    void getObjectThrowsForAbsolutePathKey() {
        IOException ex = assertThrows(IOException.class,
                () -> service.getObject("/etc/passwd"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void deleteObjectThrowsForAbsolutePathKey() {
        IOException ex = assertThrows(IOException.class,
                () -> service.deleteObject("/etc/passwd"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void getObjectThrowsForDotDotKey() {
        IOException ex = assertThrows(IOException.class,
                () -> service.getObject("../secret.txt"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void deleteObjectThrowsForDotDotKey() {
        IOException ex = assertThrows(IOException.class,
                () -> service.deleteObject("../../etc/shadow"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void getObjectThrowsForNullKey() {
        assertThrows(IOException.class,
                () -> service.getObject(null));
    }

    @Test
    void getObjectThrowsForBlankKey() {
        assertThrows(IOException.class,
                () -> service.getObject("   "));
    }

    @Test
    void getObjectSucceedsForValidKey() throws IOException {
        Path testFile = tempDir.resolve("test.jpg");
        Files.write(testFile, "data".getBytes());
        try (java.io.InputStream stream = service.getObject("test.jpg")) {
            assertArrayEquals("data".getBytes(), stream.readAllBytes());
        }
    }

    // File upload validation tests
    @Test
    void uploadObjectRejectsExecutableExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "bad.exe",
            "application/x-msdownload",
            "bad".getBytes()
        );

        IOException ex = assertThrows(IOException.class, () -> service.uploadObject(file));

        assertTrue(ex.getMessage().contains("Unsupported file type: bad.exe"));
    }

    @Test
    void uploadObjectRejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "image.png",
            "text/x-sh",
            "echo hi".getBytes()
        );

        IOException ex = assertThrows(IOException.class, () -> service.uploadObject(file));

        assertTrue(ex.getMessage().contains("Unsupported file type: image.png (text/x-sh)"));
    }

    @Test
    void uploadObjectRejectsAbsolutePathFilename() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            tempDir.resolveSibling("outside.png").toString(),
            "image/png",
            PNG_BYTES
        );

        IOException ex = assertThrows(IOException.class, () -> service.uploadObject(file));

        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void uploadObjectAllowsSupportedImageType() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "photo.PNG",
            "image/png",
            PNG_BYTES
        );

        service.uploadObject(file);

        assertTrue(Files.exists(tempDir.resolve("photo.PNG")));
        verify(rabbitTemplate).convertAndSend(eq(IMAGE_PROCESSING_QUEUE), any(ImageProcessingMessage.class));
    }
}
