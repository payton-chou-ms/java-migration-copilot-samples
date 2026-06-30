package com.microsoft.migration.assets.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {

    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Mock
    private MultipartFile multipartFile;

    @Test
    void listObjectsRequiresInitialization() {
        LocalFileStorageService service = new LocalFileStorageService(rabbitTemplate);

        assertThrows(IllegalStateException.class, service::listObjects);
    }

    @Test
    void uploadObjectRequiresInitialization() {
        LocalFileStorageService service = new LocalFileStorageService(rabbitTemplate);

        assertThrows(IllegalStateException.class, () -> service.uploadObject(multipartFile));
    }

    @Test
    void getObjectRequiresInitialization() {
        LocalFileStorageService service = new LocalFileStorageService(rabbitTemplate);

        assertThrows(IllegalStateException.class, () -> service.getObject("image.png"));
    }

    @Test
    void deleteObjectRequiresInitialization() {
        LocalFileStorageService service = new LocalFileStorageService(rabbitTemplate);

        assertThrows(IllegalStateException.class, () -> service.deleteObject("image.png"));
    }
}
