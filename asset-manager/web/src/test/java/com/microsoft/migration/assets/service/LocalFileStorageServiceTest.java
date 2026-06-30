package com.microsoft.migration.assets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService localFileStorageService;

    @BeforeEach
    void setUp() throws IOException {
        localFileStorageService = new LocalFileStorageService(mock(RabbitTemplate.class));
        ReflectionTestUtils.setField(localFileStorageService, "storageDirectory", tempDir.toString());
        localFileStorageService.init();
    }

    @Test
    void getObjectRejectsPathTraversalKey() {
        assertThrows(IOException.class, () -> localFileStorageService.getObject("../outside.txt"));
    }

    @Test
    void deleteObjectRejectsPathTraversalKey() {
        assertThrows(IOException.class, () -> localFileStorageService.deleteObject("../outside.txt"));
    }

    @Test
    void getObjectReadsFileInsideRoot() throws IOException {
        Path file = tempDir.resolve("safe.txt");
        Files.write(file, "ok".getBytes(StandardCharsets.UTF_8));

        String content;
        try (InputStream inputStream = localFileStorageService.getObject("safe.txt")) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals("ok", content);
    }

    @Test
    void uploadObjectRejectsAbsolutePathFilename() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            tempDir.resolveSibling("outside.txt").toString(),
            "text/plain",
            "blocked".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(IOException.class, () -> localFileStorageService.uploadObject(file));
    }
}
