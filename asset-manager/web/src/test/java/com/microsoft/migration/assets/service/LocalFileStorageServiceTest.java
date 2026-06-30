package com.microsoft.migration.assets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class LocalFileStorageServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LocalFileStorageService localFileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(localFileStorageService, "rootLocation", tempDir);
    }

    @Test
    void getObjectThrowsForAbsolutePathKey() {
        IOException ex = assertThrows(IOException.class,
                () -> localFileStorageService.getObject("/etc/passwd"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void deleteObjectThrowsForAbsolutePathKey() {
        IOException ex = assertThrows(IOException.class,
                () -> localFileStorageService.deleteObject("/etc/passwd"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void getObjectThrowsForDotDotKey() {
        IOException ex = assertThrows(IOException.class,
                () -> localFileStorageService.getObject("../secret.txt"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void deleteObjectThrowsForDotDotKey() {
        IOException ex = assertThrows(IOException.class,
                () -> localFileStorageService.deleteObject("../../etc/shadow"));
        assertTrue(ex.getMessage().contains("Invalid or unsafe key"));
    }

    @Test
    void getObjectThrowsForNullKey() {
        assertThrows(IOException.class,
                () -> localFileStorageService.getObject(null));
    }

    @Test
    void getObjectThrowsForBlankKey() {
        assertThrows(IOException.class,
                () -> localFileStorageService.getObject("   "));
    }

    @Test
    void getObjectSucceedsForValidKey() throws IOException {
        Path testFile = tempDir.resolve("test.jpg");
        Files.write(testFile, "data".getBytes());
        try (java.io.InputStream stream = localFileStorageService.getObject("test.jpg")) {
            assertArrayEquals("data".getBytes(), stream.readAllBytes());
        }
    }
}
