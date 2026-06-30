package com.microsoft.migration.assets.worker.service;

import com.azure.core.credential.AccessToken;
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

    private final DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();

    /**
     * Call gpt-image-2 /images/edits with the given style prompt and return the PNG bytes.
     */
    DefaultAzureCredential getCredential() {
        return credential;
    }

    public byte[] generateStyledImage(Path originalImage, String prompt) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Azure OpenAI endpoint is not configured");
        }
        byte[] imageBytes = Files.readAllBytes(originalImage);
        String boundary = "----asset" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, imageBytes, prompt);

        String url = endpoint.replaceAll("/+$", "")
                + "/openai/deployments/" + deployment
                + "/images/edits?api-version=" + apiVersion;

        AccessToken accessToken = getCredential().getToken(new TokenRequestContext().addScopes(SCOPE)).block();
        if (accessToken == null) {
            throw new IOException("Could not acquire Azure credential token");
        }
        String token = accessToken.getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }

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
        // Strip CR/LF to prevent multipart boundary injection
        String safeValue = value.replace("\r", "").replace("\n", " ");
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(safeValue.getBytes(StandardCharsets.UTF_8));
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
