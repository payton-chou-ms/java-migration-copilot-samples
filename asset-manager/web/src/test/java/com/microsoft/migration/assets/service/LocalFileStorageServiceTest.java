package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    );

    @Mock
    private ServiceBusSenderClient senderClient;

    @SuppressWarnings("unchecked")
    @Mock
    private ObjectProvider<ServiceBusSenderClient> senderClientProvider;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(senderClientProvider.getIfAvailable()).thenReturn(senderClient);
        service = new LocalFileStorageService(senderClientProvider, new ObjectMapper(), imageMetadataRepository);
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
        verify(senderClient).sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    void listObjectsRequiresInitialization() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceBusSenderClient> provider = mock(ObjectProvider.class);
        LocalFileStorageService uninitializedService = new LocalFileStorageService(provider, new ObjectMapper(), imageMetadataRepository);
        assertThrows(IllegalStateException.class, uninitializedService::listObjects);
    }

    @Test
    void uploadObjectRequiresInitialization() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceBusSenderClient> provider = mock(ObjectProvider.class);
        LocalFileStorageService uninitializedService = new LocalFileStorageService(provider, new ObjectMapper(), imageMetadataRepository);
        assertThrows(IllegalStateException.class, () -> uninitializedService.uploadObject(multipartFile));
    }

    @Test
    void getObjectRequiresInitialization() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceBusSenderClient> provider = mock(ObjectProvider.class);
        LocalFileStorageService uninitializedService = new LocalFileStorageService(provider, new ObjectMapper(), imageMetadataRepository);
        assertThrows(IllegalStateException.class, () -> uninitializedService.getObject("image.png"));
    }

    @Test
    void deleteObjectRequiresInitialization() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ServiceBusSenderClient> provider = mock(ObjectProvider.class);
        LocalFileStorageService uninitializedService = new LocalFileStorageService(provider, new ObjectMapper(), imageMetadataRepository);
        assertThrows(IllegalStateException.class, () -> uninitializedService.deleteObject("image.png"));
    }
}
