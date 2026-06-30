# AI Style Variations + Stripe UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the worker generates a thumbnail, also generate three AI style variations (Realistic 写真 / Cyberpunk 賽博龐克 / Manga 日本漫畫) of each uploaded image via Azure OpenAI `gpt-image-2`, store them in Blob Storage, and show original + 3 styles side-by-side in a Stripe-restyled web UI.

**Architecture:** Reuse the existing Upload → Service Bus → Worker → Blob/PostgreSQL pipeline. The worker's `AbstractFileProcessingService.processImage` gains a best-effort style-generation step after thumbnailing. Three style key/url field pairs are added to `ImageMetadata`. The web app is restyled with a Stripe-inspired CSS design system and the detail page renders the original on top with three style cards below.

**Tech Stack:** Java 21, Spring Boot 3.x, Azure SDK (azure-identity, azure-storage-blob), `java.net.http.HttpClient` (JDK) for the gpt-image-2 `/images/edits` REST call, Jackson, JPA/PostgreSQL, Thymeleaf.

---

## File Structure

**Worker (`asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/`)**
- Create `model/StyleVariation.java` — enum of the 3 styles (id, prompt, `keyFor`, `apply`).
- Modify `model/ImageMetadata.java` — +6 style fields.
- Create `service/ImageGenerationService.java` — calls gpt-image-2 `/images/edits`.
- Modify `service/FileProcessor.java` — add `uploadStyleImage(...)`.
- Modify `service/AbstractFileProcessingService.java` — orchestrate style generation.
- Modify `service/S3FileProcessingService.java` — implement `uploadStyleImage`.
- Modify `service/LocalFileProcessingService.java` — implement `uploadStyleImage`.
- Modify `src/main/resources/application.properties` — Azure OpenAI config.
- Create tests under `worker/src/test/java/.../worker/service/` and `.../worker/model/`.

**Web (`asset-manager/web/src/main/...`)**
- Modify `java/.../assets/model/ImageMetadata.java` — +6 style fields.
- Modify `java/.../assets/model/S3StorageItem.java` — +3 style key fields.
- Modify `java/.../assets/controller/S3Controller.java` — populate style keys on detail page.
- Create `resources/static/css/stripe-theme.css` — Stripe design system.
- Modify `resources/templates/layout.html`, `list.html`, `upload.html`, `view.html`.

---

## Task 1: Add style fields to worker `ImageMetadata`

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/model/ImageMetadata.java`

- [ ] **Step 1: Add the six style fields**

In `ImageMetadata.java`, add these fields after the existing `thumbnailUrl` field:

```java
    private String thumbnailKey;
    private String thumbnailUrl;
    private String realisticKey;
    private String realisticUrl;
    private String cyberpunkKey;
    private String cyberpunkUrl;
    private String mangaKey;
    private String mangaUrl;
    private LocalDateTime uploadedAt;
```

(Insert the six `realistic*/cyberpunk*/manga*` lines between `thumbnailUrl` and `uploadedAt`; leave the rest of the class unchanged.)

- [ ] **Step 2: Compile the worker module**

Run: `cd asset-manager && ./mvnw -q -pl worker -am compile`
Expected: BUILD SUCCESS (Lombok generates the new getters/setters).

- [ ] **Step 3: Commit**

```bash
git add asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/model/ImageMetadata.java
git commit -m "feat(worker): add style variation fields to ImageMetadata"
```

---

## Task 2: Create the `StyleVariation` enum (TDD)

**Files:**
- Create: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/model/StyleVariation.java`
- Test: `asset-manager/worker/src/test/java/com/microsoft/migration/assets/worker/model/StyleVariationTest.java`

- [ ] **Step 1: Write the failing test**

Create `StyleVariationTest.java`:

```java
package com.microsoft.migration.assets.worker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StyleVariationTest {

    @Test
    void keyForInsertsStyleIdBeforeExtension() {
        assertEquals("abc-photo_realistic.png",
                StyleVariation.REALISTIC.keyFor("abc-photo.png"));
        assertEquals("abc-photo_cyberpunk.jpg",
                StyleVariation.CYBERPUNK.keyFor("abc-photo.jpg"));
    }

    @Test
    void keyForHandlesKeyWithoutExtension() {
        assertEquals("photo_manga", StyleVariation.MANGA.keyFor("photo"));
    }

    @Test
    void applySetsOnlyTheMatchingFieldPair() {
        ImageMetadata m = new ImageMetadata();
        StyleVariation.CYBERPUNK.apply(m, "k.png", "http://u/k.png");

        assertEquals("k.png", m.getCyberpunkKey());
        assertEquals("http://u/k.png", m.getCyberpunkUrl());
        assertNull(m.getRealisticKey());
        assertNull(m.getMangaKey());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd asset-manager && ./mvnw -q -pl worker -am -Dtest=StyleVariationTest test`
Expected: FAIL — `StyleVariation` does not exist (compilation error).

- [ ] **Step 3: Create the `StyleVariation` enum**

Create `StyleVariation.java`:

```java
package com.microsoft.migration.assets.worker.model;

public enum StyleVariation {

    REALISTIC("realistic",
        "Transform this image into a photorealistic studio portrait photograph. " +
        "Natural soft lighting, shallow depth of field, sharp focus, realistic skin and " +
        "textures. Keep the original subject, pose, and composition."),

    CYBERPUNK("cyberpunk",
        "Reimagine this image in a cyberpunk style. Neon magenta and electric indigo " +
        "lighting, glowing holographic signage, rain-slicked futuristic city atmosphere, " +
        "high-tech dystopian mood. Keep the original subject, pose, and composition."),

    MANGA("manga",
        "Redraw this image as black-and-white Japanese manga line art. Bold ink outlines, " +
        "screentone shading, dramatic hatching, expressive comic style. Keep the original " +
        "subject, pose, and composition.");

    private final String id;
    private final String prompt;

    StyleVariation(String id, String prompt) {
        this.id = id;
        this.prompt = prompt;
    }

    public String getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    /** Build the blob key for this style, e.g. "abc-photo.png" -> "abc-photo_realistic.png". */
    public String keyFor(String originalKey) {
        int dot = originalKey.lastIndexOf('.');
        if (dot > 0) {
            return originalKey.substring(0, dot) + "_" + id + originalKey.substring(dot);
        }
        return originalKey + "_" + id;
    }

    /** Set this style's key/url pair on the given metadata row. */
    public void apply(ImageMetadata metadata, String key, String url) {
        switch (this) {
            case REALISTIC -> {
                metadata.setRealisticKey(key);
                metadata.setRealisticUrl(url);
            }
            case CYBERPUNK -> {
                metadata.setCyberpunkKey(key);
                metadata.setCyberpunkUrl(url);
            }
            case MANGA -> {
                metadata.setMangaKey(key);
                metadata.setMangaUrl(url);
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd asset-manager && ./mvnw -q -pl worker -am -Dtest=StyleVariationTest test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/model/StyleVariation.java \
        asset-manager/worker/src/test/java/com/microsoft/migration/assets/worker/model/StyleVariationTest.java
git commit -m "feat(worker): add StyleVariation enum with key and metadata helpers"
```

---

## Task 3: Add Azure OpenAI configuration

**Files:**
- Modify: `asset-manager/worker/src/main/resources/application.properties`

- [ ] **Step 1: Append the Azure OpenAI config block**

Add at the end of `application.properties`:

```properties

# Azure OpenAI (gpt-image-2) for AI style variations (passwordless / managed identity)
azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
azure.openai.image-deployment=${AZURE_OPENAI_IMAGE_DEPLOYMENT:gpt-image-2}
azure.openai.image-api-version=${AZURE_OPENAI_IMAGE_API_VERSION:2025-04-01-preview}
azure.openai.image-size=${AZURE_OPENAI_IMAGE_SIZE:1024x1024}
```

- [ ] **Step 2: Commit**

```bash
git add asset-manager/worker/src/main/resources/application.properties
git commit -m "chore(worker): add Azure OpenAI gpt-image-2 config properties"
```

---

## Task 4: Create `ImageGenerationService` (TDD on parser + config gate)

**Files:**
- Create: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/ImageGenerationService.java`
- Test: `asset-manager/worker/src/test/java/com/microsoft/migration/assets/worker/service/ImageGenerationServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `ImageGenerationServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd asset-manager && ./mvnw -q -pl worker -am -Dtest=ImageGenerationServiceTest test`
Expected: FAIL — `ImageGenerationService` does not exist.

- [ ] **Step 3: Create `ImageGenerationService`**

Create `ImageGenerationService.java`:

```java
package com.microsoft.migration.assets.worker.service;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

@Service
@Slf4j
public class ImageGenerationService {

    private static final String SCOPE = "https://cognitiveservices.azure.com/.default";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();

    @Value("${azure.openai.endpoint:}")
    String endpoint;

    @Value("${azure.openai.image-deployment:gpt-image-2}")
    String deployment;

    @Value("${azure.openai.image-api-version:2025-04-01-preview}")
    String apiVersion;

    @Value("${azure.openai.image-size:1024x1024}")
    String size;

    public ImageGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** True when an Azure OpenAI endpoint is configured. */
    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank();
    }

    /**
     * Call gpt-image-2 /images/edits with the given style prompt and return the PNG bytes.
     */
    public byte[] generateStyledImage(Path originalImage, String prompt) throws Exception {
        byte[] imageBytes = Files.readAllBytes(originalImage);
        String boundary = "----asset" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, imageBytes, prompt);

        String url = endpoint.replaceAll("/+$", "")
                + "/openai/deployments/" + deployment
                + "/images/edits?api-version=" + apiVersion;

        String token = credential.getToken(new TokenRequestContext().addScopes(SCOPE))
                .block().getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("gpt-image-2 returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        return parseImageBytes(response.body());
    }

    /** Extract and base64-decode data[0].b64_json from the API response. */
    byte[] parseImageBytes(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode b64 = root.path("data").path(0).path("b64_json");
        if (b64.isMissingNode() || b64.asText().isEmpty()) {
            throw new IOException("No image data in response: " + json);
        }
        return Base64.getDecoder().decode(b64.asText());
    }

    private byte[] buildMultipartBody(String boundary, byte[] imageBytes, String prompt)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextPart(out, boundary, "prompt", prompt);
        writeTextPart(out, boundary, "n", "1");
        writeTextPart(out, boundary, "size", size);
        writeFilePart(out, boundary, "image", "image.png", "image/png", imageBytes);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream out, String boundary, String name,
                               String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(ByteArrayOutputStream out, String boundary, String name,
                               String filename, String contentType, byte[] content)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd asset-manager && ./mvnw -q -pl worker -am -Dtest=ImageGenerationServiceTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/ImageGenerationService.java \
        asset-manager/worker/src/test/java/com/microsoft/migration/assets/worker/service/ImageGenerationServiceTest.java
git commit -m "feat(worker): add ImageGenerationService for gpt-image-2 edits"
```

---

## Task 5: Add `uploadStyleImage` to `FileProcessor` and implementations

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/FileProcessor.java`
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/S3FileProcessingService.java`
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/LocalFileProcessingService.java`

- [ ] **Step 1: Add the interface method**

Replace the body of `FileProcessor.java` with:

```java
package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.worker.model.StyleVariation;

import java.nio.file.Path;

public interface FileProcessor {
    void downloadOriginal(String key, Path destination) throws Exception;
    void uploadThumbnail(Path source, String key, String contentType) throws Exception;
    void uploadStyleImage(Path source, String key, String contentType,
                          String originalKey, StyleVariation style) throws Exception;
    String getStorageType();
}
```

- [ ] **Step 2: Implement in `S3FileProcessingService`**

Add this import near the other model imports in `S3FileProcessingService.java`:

```java
import com.microsoft.migration.assets.worker.model.StyleVariation;
```

Add this method after the existing `uploadThumbnail` method:

```java
    @Override
    public void uploadStyleImage(Path source, String key, String contentType,
                                 String originalKey, StyleVariation style) throws Exception {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(key);
        blobClient.uploadFromFile(source.toString(), true);
        blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));

        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(originalKey))
                .findFirst()
                .ifPresent(metadata -> {
                    style.apply(metadata, key, generateUrl(key));
                    imageMetadataRepository.save(metadata);
                });
    }
```

- [ ] **Step 3: Implement in `LocalFileProcessingService`**

Add this import in `LocalFileProcessingService.java`:

```java
import com.microsoft.migration.assets.worker.model.StyleVariation;
```

Add this method after the existing `uploadThumbnail` method:

```java
    @Override
    public void uploadStyleImage(Path source, String key, String contentType,
                                 String originalKey, StyleVariation style) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        // Dev/local profile stores files only; metadata is managed by the web app's local service.
    }
```

- [ ] **Step 4: Compile the worker module**

Run: `cd asset-manager && ./mvnw -q -pl worker -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/FileProcessor.java \
        asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/S3FileProcessingService.java \
        asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/LocalFileProcessingService.java
git commit -m "feat(worker): add uploadStyleImage to processors"
```

---

## Task 6: Orchestrate style generation in `AbstractFileProcessingService`

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`

- [ ] **Step 1: Add imports and the injected service**

In `AbstractFileProcessingService.java`, add to the imports:

```java
import com.microsoft.migration.assets.worker.model.StyleVariation;
```

Add this field next to the existing `@Autowired` fields (after `processorClientProvider`):

```java
    @Autowired
    private ImageGenerationService imageGenerationService;
```

- [ ] **Step 2: Call style generation after the thumbnail upload**

In `processImage(...)`, find this block:

```java
                // Upload thumbnail
                String thumbnailKey = StorageUtil.getThumbnailKey(message.getKey());
                uploadThumbnail(thumbnailFile, thumbnailKey, message.getContentType());

                log.info("Successfully processed image: {}", message.getKey());

                // Mark processing as successful
                processingSuccess = true;
```

Replace it with:

```java
                // Upload thumbnail
                String thumbnailKey = StorageUtil.getThumbnailKey(message.getKey());
                uploadThumbnail(thumbnailFile, thumbnailKey, message.getContentType());

                // Generate AI style variations (best-effort; failures do not fail the message)
                generateStyleVariations(message, originalFile);

                log.info("Successfully processed image: {}", message.getKey());

                // Mark processing as successful
                processingSuccess = true;
```

- [ ] **Step 3: Add the `generateStyleVariations` helper**

Add this method to the class (e.g. directly after `processImage`):

```java
    private void generateStyleVariations(ImageProcessingMessage message, Path originalFile) {
        if (!imageGenerationService.isConfigured()) {
            log.info("Azure OpenAI not configured; skipping style variations for {}",
                    message.getKey());
            return;
        }

        for (StyleVariation style : StyleVariation.values()) {
            Path styleFile = null;
            try {
                byte[] bytes = imageGenerationService.generateStyledImage(
                        originalFile, style.getPrompt());
                String styleKey = style.keyFor(message.getKey());
                styleFile = Files.createTempFile("style-" + style.getId(), ".png");
                Files.write(styleFile, bytes);
                uploadStyleImage(styleFile, styleKey, "image/png", message.getKey(), style);
                log.info("Generated {} style for {}", style.getId(), message.getKey());
            } catch (Exception e) {
                log.error("Failed to generate {} style for {}: {}",
                        style.getId(), message.getKey(), e.getMessage());
            } finally {
                if (styleFile != null) {
                    try {
                        Files.deleteIfExists(styleFile);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                }
            }
        }
    }
```

- [ ] **Step 4: Compile and run worker tests**

Run: `cd asset-manager && ./mvnw -q -pl worker -am test`
Expected: BUILD SUCCESS, all worker tests pass (existing + StyleVariationTest + ImageGenerationServiceTest).

- [ ] **Step 5: Commit**

```bash
git add asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java
git commit -m "feat(worker): generate style variations after thumbnail (best-effort)"
```

---

## Task 7: Add style fields to web `ImageMetadata`

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/model/ImageMetadata.java`

- [ ] **Step 1: Add the six style fields**

In the web `ImageMetadata.java`, insert the six fields between `thumbnailUrl` and `uploadedAt` (identical to Task 1):

```java
    private String thumbnailKey;
    private String thumbnailUrl;
    private String realisticKey;
    private String realisticUrl;
    private String cyberpunkKey;
    private String cyberpunkUrl;
    private String mangaKey;
    private String mangaUrl;
    private LocalDateTime uploadedAt;
```

- [ ] **Step 2: Compile the web module**

Run: `cd asset-manager && ./mvnw -q -pl web -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add asset-manager/web/src/main/java/com/microsoft/migration/assets/model/ImageMetadata.java
git commit -m "feat(web): add style variation fields to ImageMetadata"
```

---

## Task 8: Expose style keys to the detail page

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/model/S3StorageItem.java`
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java`

- [ ] **Step 1: Add style key fields to `S3StorageItem` (keep the existing 6-arg constructor)**

Replace the contents of `S3StorageItem.java` with:

```java
package com.microsoft.migration.assets.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class S3StorageItem {
    private String key;
    private String name;
    private long size;
    private Instant lastModified;
    private Instant uploadedAt;
    private String url;
    private String realisticKey;
    private String cyberpunkKey;
    private String mangaKey;

    public S3StorageItem(String key, String name, long size, Instant lastModified,
                         Instant uploadedAt, String url) {
        this.key = key;
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.uploadedAt = uploadedAt;
        this.url = url;
    }
}
```

(Dropping `@AllArgsConstructor` and adding an explicit 6-arg constructor keeps the existing `new S3StorageItem(key, name, size, lastModified, uploadedAt, url)` call sites in `AwsS3Service` and `LocalFileStorageService` working unchanged.)

- [ ] **Step 2: Populate style keys in `S3Controller.viewObjectPage`**

In `S3Controller.java`, add the repository import and field, then enrich the item.

Add import:

```java
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
```

Add the field next to the existing `storageService` field:

```java
    private final StorageService storageService;
    private final ImageMetadataRepository imageMetadataRepository;
```

In `viewObjectPage`, replace this block:

```java
            if (foundObject.isPresent()) {
                model.addAttribute("object", foundObject.get());
                return "view";
            } else {
```

with:

```java
            if (foundObject.isPresent()) {
                S3StorageItem item = foundObject.get();
                imageMetadataRepository.findAll().stream()
                        .filter(metadata -> key.equals(metadata.getS3Key()))
                        .findFirst()
                        .ifPresent(metadata -> {
                            item.setRealisticKey(metadata.getRealisticKey());
                            item.setCyberpunkKey(metadata.getCyberpunkKey());
                            item.setMangaKey(metadata.getMangaKey());
                        });
                model.addAttribute("object", item);
                return "view";
            } else {
```

- [ ] **Step 3: Compile the web module**

Run: `cd asset-manager && ./mvnw -q -pl web -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add asset-manager/web/src/main/java/com/microsoft/migration/assets/model/S3StorageItem.java \
        asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java
git commit -m "feat(web): expose style variation keys on the detail page"
```

---

## Task 9: Create the Stripe theme and apply it in `layout.html`

**Files:**
- Create: `asset-manager/web/src/main/resources/static/css/stripe-theme.css`
- Modify: `asset-manager/web/src/main/resources/templates/layout.html`

- [ ] **Step 1: Create the Stripe stylesheet**

Create `stripe-theme.css`:

```css
:root {
  --indigo: #533afd;
  --indigo-deep: #4434d4;
  --ink: #0d253d;
  --muted: #64748d;
  --hair: #e3e8ee;
  --canvas: #ffffff;
  --canvas-alt: #f6f9fc;
  --ruby: #ea2261;
  --magenta: #f96bee;
  --radius-md: 8px;
  --radius-lg: 16px;
}

* { box-sizing: border-box; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  color: var(--ink);
  background: var(--canvas-alt);
  margin: 0;
  line-height: 1.55;
}

.sx-container { max-width: 1100px; margin: 0 auto; padding: 32px 24px 64px; }

.sx-topbar {
  background: #fff;
  border-bottom: 1px solid var(--hair);
  padding: 18px 24px;
  position: relative;
  overflow: hidden;
}
.sx-topbar::before {
  content: "";
  position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, var(--indigo), var(--ruby), var(--magenta));
}
.sx-topbar-inner {
  max-width: 1100px; margin: 0 auto;
  display: flex; align-items: center; gap: 16px;
}
.sx-brand { font-size: 20px; font-weight: 300; letter-spacing: -0.5px; }
.sx-brand b { font-weight: 700; color: var(--indigo); }
.sx-nav { margin-left: auto; display: flex; gap: 12px; }

.sx-btn {
  border-radius: 9999px;
  padding: 9px 20px;
  font-size: 15px;
  font-weight: 700;
  border: 1px solid transparent;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex; align-items: center; gap: 6px;
  transition: background .15s, border-color .15s, color .15s;
}
.sx-btn-primary { background: var(--indigo); color: #fff; }
.sx-btn-primary:hover { background: var(--indigo-deep); }
.sx-btn-outline { background: #fff; color: var(--ink); border-color: var(--hair); }
.sx-btn-outline:hover { border-color: var(--indigo); color: var(--indigo); }
.sx-btn-danger { background: #fff; color: var(--ruby); border-color: var(--hair); }
.sx-btn-danger:hover { background: var(--ruby); color: #fff; border-color: var(--ruby); }
.sx-btn-sm { padding: 6px 14px; font-size: 13px; }

.sx-eyebrow {
  font-size: 12px; font-weight: 700; letter-spacing: 0.96px;
  text-transform: uppercase; color: var(--indigo);
}
.sx-h1 { font-size: 34px; font-weight: 300; letter-spacing: -1px; margin: 6px 0 24px; }
.sx-h2 { font-size: 22px; font-weight: 700; letter-spacing: -0.3px; margin: 0; }

.sx-card {
  background: #fff;
  border: 1px solid var(--hair);
  border-radius: var(--radius-lg);
  box-shadow: 0 2px 5px rgba(60,66,87,.08), 0 1px 1px rgba(0,0,0,.04);
  overflow: hidden;
}

.sx-grid { display: grid; gap: 20px; }
.sx-grid-3 { grid-template-columns: repeat(3, 1fr); }
.sx-grid-cards { grid-template-columns: repeat(3, 1fr); }

.sx-alert { border-radius: var(--radius-md); padding: 12px 16px; margin-bottom: 20px; font-size: 14px; }
.sx-alert-success { background: #ecfdf3; color: #027a48; border: 1px solid #abefc6; }
.sx-alert-danger { background: #fef3f2; color: #b42318; border: 1px solid #fecdca; }
.sx-alert-info { background: var(--canvas-alt); color: var(--muted); border: 1px solid var(--hair); }

.sx-muted { color: var(--muted); }
.sx-tag {
  font-size: 11px; font-weight: 700; letter-spacing: .5px; text-transform: uppercase;
  color: var(--indigo); background: #efeefe; border-radius: 9999px; padding: 4px 10px;
  display: inline-block;
}
.sx-crumb { font-size: 13px; color: var(--muted); margin-bottom: 8px; }
.sx-crumb a { color: var(--indigo); text-decoration: none; }

@media (max-width: 1023px) { .sx-grid-3, .sx-grid-cards { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 767px)  { .sx-grid-3, .sx-grid-cards { grid-template-columns: 1fr; } }
```

- [ ] **Step 2: Rewrite `layout.html` to use the Stripe theme**

Replace the entire contents of `layout.html` with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:fragment="layout(title, content)">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${title}">Asset Studio</title>
    <link rel="stylesheet" th:href="@{/css/stripe-theme.css}" href="/css/stripe-theme.css">
</head>
<body>
    <div class="sx-topbar">
        <div class="sx-topbar-inner">
            <div class="sx-brand">Asset <b>Studio</b></div>
            <div class="sx-nav">
                <a class="sx-btn sx-btn-outline" th:href="@{/storage}">All Images</a>
                <a class="sx-btn sx-btn-primary" th:href="@{/storage/upload}">Upload New Image</a>
            </div>
        </div>
    </div>

    <div class="sx-container">
        <div th:if="${success}" class="sx-alert sx-alert-success" role="alert">
            <span th:text="${success}">Success message</span>
        </div>
        <div th:if="${error}" class="sx-alert sx-alert-danger" role="alert">
            <span th:text="${error}">Error message</span>
        </div>

        <div th:replace="${content}">
            Page content goes here
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git add asset-manager/web/src/main/resources/static/css/stripe-theme.css \
        asset-manager/web/src/main/resources/templates/layout.html
git commit -m "feat(web): add Stripe-inspired theme and restyle layout"
```

---

## Task 10: Rewrite the detail page (`view.html`) — layout C

**Files:**
- Modify: `asset-manager/web/src/main/resources/templates/view.html`

- [ ] **Step 1: Replace `view.html` with the original-on-top + 3-styles layout**

Replace the entire contents of `view.html` with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{layout :: layout('View Image', ~{::content})}">
<body>
    <div th:fragment="content">
        <div class="sx-crumb">
            <a th:href="@{/storage}">Images</a> &nbsp;/&nbsp;
            <span th:text="${object.name}">Image Name</span>
        </div>
        <div class="sx-eyebrow">Asset detail</div>
        <h1 class="sx-h1" th:text="${object.name}">Image Name</h1>

        <div class="sx-card" style="margin-bottom:28px">
            <div style="text-align:center; background:var(--canvas-alt)">
                <img th:src="@{'/storage/view/' + ${object.key}}" alt="Original"
                     style="max-width:100%; max-height:60vh; object-fit:contain;">
            </div>
            <div style="display:flex; align-items:center; gap:20px; padding:16px 22px;
                        border-top:1px solid var(--hair); font-size:14px; flex-wrap:wrap">
                <span><span class="sx-muted">Size:</span>
                    <strong th:text="${#numbers.formatDecimal(object.size / 1024, 0, 2) + ' KB'}">0 KB</strong></span>
                <span><span class="sx-muted">Uploaded:</span>
                    <strong th:text="${#temporals.format(object.uploadedAt, 'dd-MM-yyyy HH:mm')}">Date</strong></span>
                <span style="margin-left:auto; display:flex; gap:10px">
                    <a th:href="@{'/storage/view/' + ${object.key}}" download class="sx-btn sx-btn-outline sx-btn-sm">Download</a>
                    <form th:action="@{'/storage/delete/' + ${object.key}}" method="post"
                          onsubmit="return confirm('Are you sure you want to delete this file?');">
                        <button type="submit" class="sx-btn sx-btn-danger sx-btn-sm">Delete</button>
                    </form>
                </span>
            </div>
        </div>

        <div style="display:flex; align-items:baseline; gap:12px; margin:6px 0 16px">
            <h2 class="sx-h2">AI Style Variations</h2>
            <span class="sx-muted" style="font-size:14px">Generated by gpt-image-2 · 3 styles</span>
        </div>

        <div class="sx-grid sx-grid-3">
            <!-- Realistic -->
            <div class="sx-card">
                <div th:if="${object.realisticKey}" style="background:var(--canvas-alt); text-align:center">
                    <img th:src="@{'/storage/view/' + ${object.realisticKey}}" alt="Realistic"
                         style="width:100%; height:210px; object-fit:cover">
                </div>
                <div th:unless="${object.realisticKey}"
                     style="height:210px; display:flex; align-items:center; justify-content:center;
                            background:repeating-linear-gradient(45deg,#f0f2f7 0 10px,#f6f9fc 10px 20px); color:var(--muted)">
                    Unavailable
                </div>
                <div style="padding:14px 18px; display:flex; justify-content:space-between; align-items:center">
                    <div><span class="sx-tag">Realistic</span>
                        <div style="font-weight:700; margin-top:8px">寫真風格</div></div>
                    <a th:if="${object.realisticKey}" th:href="@{'/storage/view/' + ${object.realisticKey}}"
                       download class="sx-btn sx-btn-outline sx-btn-sm">↓</a>
                </div>
            </div>
            <!-- Cyberpunk -->
            <div class="sx-card">
                <div th:if="${object.cyberpunkKey}" style="background:var(--canvas-alt); text-align:center">
                    <img th:src="@{'/storage/view/' + ${object.cyberpunkKey}}" alt="Cyberpunk"
                         style="width:100%; height:210px; object-fit:cover">
                </div>
                <div th:unless="${object.cyberpunkKey}"
                     style="height:210px; display:flex; align-items:center; justify-content:center;
                            background:repeating-linear-gradient(45deg,#f0f2f7 0 10px,#f6f9fc 10px 20px); color:var(--muted)">
                    Unavailable
                </div>
                <div style="padding:14px 18px; display:flex; justify-content:space-between; align-items:center">
                    <div><span class="sx-tag">Cyberpunk</span>
                        <div style="font-weight:700; margin-top:8px">賽博龐克</div></div>
                    <a th:if="${object.cyberpunkKey}" th:href="@{'/storage/view/' + ${object.cyberpunkKey}}"
                       download class="sx-btn sx-btn-outline sx-btn-sm">↓</a>
                </div>
            </div>
            <!-- Manga -->
            <div class="sx-card">
                <div th:if="${object.mangaKey}" style="background:var(--canvas-alt); text-align:center">
                    <img th:src="@{'/storage/view/' + ${object.mangaKey}}" alt="Manga"
                         style="width:100%; height:210px; object-fit:cover">
                </div>
                <div th:unless="${object.mangaKey}"
                     style="height:210px; display:flex; align-items:center; justify-content:center;
                            background:repeating-linear-gradient(45deg,#f0f2f7 0 10px,#f6f9fc 10px 20px); color:var(--muted)">
                    Unavailable
                </div>
                <div style="padding:14px 18px; display:flex; justify-content:space-between; align-items:center">
                    <div><span class="sx-tag">Manga</span>
                        <div style="font-weight:700; margin-top:8px">日本漫畫</div></div>
                    <a th:if="${object.mangaKey}" th:href="@{'/storage/view/' + ${object.mangaKey}}"
                       download class="sx-btn sx-btn-outline sx-btn-sm">↓</a>
                </div>
            </div>
        </div>

        <p class="sx-muted" style="font-size:13px; margin-top:18px">
            Styles appear here as the worker finishes generating them. A missing style shows
            "Unavailable" — the others are unaffected.
        </p>

        <div style="margin-top:24px">
            <a th:href="@{/storage}" class="sx-btn sx-btn-outline">Back to Images</a>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add asset-manager/web/src/main/resources/templates/view.html
git commit -m "feat(web): redesign detail page with original + 3 style variations"
```

---

## Task 11: Restyle the gallery (`list.html`) — keep the auto-refresh script

**Files:**
- Modify: `asset-manager/web/src/main/resources/templates/list.html`

- [ ] **Step 1: Replace only the visible markup, keeping the `<script>` block intact**

In `list.html`, replace everything from `<h2>Your Images</h2>` down to and including the closing `</div>` of the `alert-info` block (i.e. the markup **above** the `<!-- Loading indicator for auto-refreshing -->` comment) with:

```html
        <div class="sx-eyebrow">Gallery</div>
        <h1 class="sx-h1">Your Images</h1>

        <div class="sx-grid sx-grid-cards" id="imageContainer" th:if="${not #lists.isEmpty(objects)}">
            <div class="sx-card" th:each="object : ${objects}" th:attr="data-key=${object.key}">
                <div style="background:var(--canvas-alt)">
                    <img th:src="@{'/storage/view/' + ${object.key}}" alt="Image preview"
                         style="width:100%; height:200px; object-fit:cover;">
                </div>
                <div style="padding:16px 18px">
                    <div style="font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis"
                         th:text="${object.name}">Image name</div>
                    <div class="sx-muted" style="font-size:13px; margin:6px 0 14px">
                        <span th:text="${#numbers.formatDecimal(object.size / 1024, 0, 2) + ' KB'}">0 KB</span>
                        · <span th:text="${#temporals.format(object.lastModified, 'dd-MM-yyyy HH:mm')}">Date</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center">
                        <a th:href="@{'/storage/view-page/' + ${object.key}}" class="sx-btn sx-btn-primary sx-btn-sm">View</a>
                        <form th:action="@{'/storage/delete/' + ${object.key}}" method="post"
                              onsubmit="return confirm('Are you sure you want to delete this file?');">
                            <button type="submit" class="sx-btn sx-btn-danger sx-btn-sm">Delete</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>

        <div class="sx-alert sx-alert-info" th:if="${#lists.isEmpty(objects)}">
            No images found. <a th:href="@{/storage/upload}" style="color:var(--indigo)">Upload your first image!</a>
        </div>
```

**Important:** Do not touch the `<!-- Loading indicator -->` block or the `<script th:inline="javascript">...</script>` block below it — they keep the existing auto-refresh behavior and must remain. The `id="imageContainer"` and `data-key` attributes above are required by that script.

- [ ] **Step 2: Replace the loading indicator markup with theme classes (optional but consistent)**

Replace the loading-indicator block:

```html
        <!-- Loading indicator for auto-refreshing -->
        <div id="refreshIndicator" style="display: none">
            <div class="d-flex justify-content-center">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </div>
            <p class="text-center mt-2">Checking for new thumbnails...</p>
        </div>
```

with:

```html
        <!-- Loading indicator for auto-refreshing -->
        <div id="refreshIndicator" style="display: none">
            <p class="sx-muted" style="text-align:center; margin-top:16px">Checking for new thumbnails…</p>
        </div>
```

- [ ] **Step 3: Commit**

```bash
git add asset-manager/web/src/main/resources/templates/list.html
git commit -m "feat(web): restyle gallery with Stripe theme"
```

---

## Task 12: Restyle the upload page (`upload.html`) — keep the JS

**Files:**
- Modify: `asset-manager/web/src/main/resources/templates/upload.html`

- [ ] **Step 1: Replace the visible markup above the `<script>`**

In `upload.html`, replace everything from `<h2>Upload Image to S3</h2>` down to the end of the `<style>...</style>` block (i.e. everything **before** the first `<script>`) with:

```html
        <div class="sx-eyebrow">Upload</div>
        <h1 class="sx-h1">Upload an image</h1>

        <form th:action="@{/storage/upload}" method="post" enctype="multipart/form-data" id="uploadForm">
            <div class="sx-card" style="padding:24px">
                <label for="file" style="font-weight:700; display:block; margin-bottom:8px">Select image</label>
                <input type="file" id="file" name="file" accept="image/*" required
                       style="display:block; width:100%; padding:10px; border:1px solid var(--hair); border-radius:8px">
                <p class="sx-muted" style="font-size:13px; margin:8px 0 0">
                    Supported: JPG, PNG, GIF, etc. Maximum size: 10MB.
                </p>

                <div id="dropZone" style="margin-top:20px; padding:40px; text-align:center;
                     border:2px dashed var(--hair); border-radius:12px; cursor:pointer; transition:all .2s">
                    <p style="margin:0; font-weight:600">Drag and drop your image here</p>
                    <p class="sx-muted" style="margin:6px 0 0; font-size:13px">(or use the selector above)</p>
                </div>
            </div>

            <div style="margin-top:20px; display:flex; gap:12px">
                <button type="submit" class="sx-btn sx-btn-primary" id="uploadBtn">Upload</button>
                <a th:href="@{/storage}" class="sx-btn sx-btn-outline">Cancel</a>
            </div>
        </form>

        <div class="sx-card" style="margin-top:20px; padding:20px; display:none;" id="imagePreview">
            <div class="sx-eyebrow">Preview</div>
            <img id="preview" style="max-width:100%; max-height:300px; margin-top:10px" alt="Image preview">
        </div>

        <style>
            #dropZone.dragover { background: #efeefe; border-color: var(--indigo) !important; }
        </style>
```

**Important:** Keep the `<script>...</script>` block(s) below unchanged. They reference the element IDs `file`, `dropZone`, `uploadForm`, `imagePreview`, and `preview`, all of which are preserved above.

- [ ] **Step 2: Build the web module and run its tests**

Run: `cd asset-manager && ./mvnw -q -pl web -am test`
Expected: BUILD SUCCESS; existing `LocalFileStorageServiceTest` passes.

- [ ] **Step 3: Commit**

```bash
git add asset-manager/web/src/main/resources/templates/upload.html
git commit -m "feat(web): restyle upload page with Stripe theme"
```

---

## Task 13: Full build verification

**Files:** none (verification only)

- [ ] **Step 1: Build and test the whole project**

Run: `cd asset-manager && ./mvnw -q clean test`
Expected: BUILD SUCCESS across `web` and `worker`; all tests pass.

- [ ] **Step 2: Package (sanity check both apps build runnable jars)**

Run: `cd asset-manager && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS; jars produced under `web/target/` and `worker/target/`.

- [ ] **Step 3: Commit any incidental fixes (only if needed)**

```bash
git add -A
git commit -m "chore: build fixes for style variations feature" || echo "nothing to commit"
```

---

## Self-Review Notes

- **Spec coverage:** Generation method (Tasks 4, 6), managed-identity auth (Task 4), data model +6 fields (Tasks 1, 7), best-effort flow (Task 6), full Stripe restyle (Tasks 9–12), detail layout C (Task 10), config placeholders (Task 3), edge cases / "Unavailable" (Task 10), testing (Tasks 2, 4, 12, 13) — all covered.
- **Type consistency:** `StyleVariation.keyFor` / `apply`, `ImageGenerationService.isConfigured` / `generateStyledImage` / `parseImageBytes`, and `FileProcessor.uploadStyleImage` signatures are used identically across worker tasks. `S3StorageItem` style getters (`getRealisticKey`, `getCyberpunkKey`, `getMangaKey`) match the Thymeleaf `object.realisticKey` etc. in `view.html`.
- **Dev-profile note:** In the `dev` (local) profile there is no Azure OpenAI endpoint, so generation is skipped and the detail page shows "Unavailable" for all three styles — expected; the Azure path is the target.
- **No new Maven dependencies:** the gpt-image-2 call uses the JDK `HttpClient` plus the already-present `azure-identity` and `jackson-databind`.
