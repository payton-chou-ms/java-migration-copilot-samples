package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import com.microsoft.migration.assets.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ControllerTest {

    @Mock
    private StorageService storageService;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @Test
    void listObjectsExcludesGeneratedArtifactsFromGallery() {
        S3Controller controller = new S3Controller(storageService, imageMetadataRepository);
        S3StorageItem original = item("upload.png");
        when(storageService.listObjects()).thenReturn(List.of(
                original,
                item("upload_thumbnail.png"),
                item("upload_realistic.png"),
                item("upload_cyberpunk.png"),
                item("upload_manga.png")
        ));
        when(imageMetadataRepository.findFirstByS3Key("upload.png")).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        String viewName = controller.listObjects(model);

        assertEquals("list", viewName);
        @SuppressWarnings("unchecked")
        List<S3StorageItem> objects = (List<S3StorageItem>) model.getAttribute("objects");
        assertEquals(List.of(original), objects);
    }

    private static S3StorageItem item(String key) {
        return new S3StorageItem(key, key, 1L, Instant.EPOCH, Instant.EPOCH, "/storage/view/" + key);
    }
}