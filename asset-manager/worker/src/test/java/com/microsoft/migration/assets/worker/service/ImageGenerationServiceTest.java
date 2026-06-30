package com.microsoft.migration.assets.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGenerationServiceTest {

    private ImageGenerationService newService(String endpoint) {
        ImageGenerationService service = new ImageGenerationService(new ObjectMapper());
        service.endpoint = endpoint;
        service.deployment = "gpt-image-2";
        service.apiVersion = "2025-04-01-preview";
        service.size = "1024x1024";
        return service;
    }

    @Test
    void isConfiguredReflectsEndpointPresence() {
        assertFalse(newService("").isConfigured());
        assertFalse(newService("   ").isConfigured());
        assertTrue(newService("https://x.openai.azure.com/").isConfigured());
    }

    @Test
    void parseImageBytesDecodesFirstB64Json() throws Exception {
        byte[] original = new byte[] {1, 2, 3, 4, 5};
        String b64 = Base64.getEncoder().encodeToString(original);
        String json = "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}";

        byte[] decoded = newService("https://x").parseImageBytes(json);

        assertArrayEquals(original, decoded);
    }
}
