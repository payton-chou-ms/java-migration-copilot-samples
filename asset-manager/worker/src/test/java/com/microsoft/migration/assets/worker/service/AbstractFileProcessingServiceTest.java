package com.microsoft.migration.assets.worker.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFileProcessingServiceTest {

    @Test
    void generateThumbnailWritesPngOutput() throws Exception {
        TestFileProcessingService service = new TestFileProcessingService();
        Path input = Files.createTempFile("thumbnail-input", ".png");
        Path output = Files.createTempFile("thumbnail-output", ".png");

        try {
            BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(image, "png", input.toFile());

            service.generate(input, output);

            assertTrue(Files.size(output) > 0);
            assertNotNull(ImageIO.read(output.toFile()));
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private static final class TestFileProcessingService extends AbstractFileProcessingService {

        @Override
        public void downloadOriginal(String key, Path destination) {
        }

        @Override
        public void uploadThumbnail(Path source, String key, String contentType) {
        }

        @Override
        public String getStorageType() {
            return "test";
        }

        @Override
        protected String generateUrl(String key) {
            return key;
        }

        private void generate(Path input, Path output) throws java.io.IOException {
            generateThumbnail(input, output);
        }
    }
}
