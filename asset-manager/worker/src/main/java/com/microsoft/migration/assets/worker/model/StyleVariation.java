package com.microsoft.migration.assets.worker.model;

import java.util.Objects;

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

    /** Build the blob key for this style, e.g. "photo.png" -> "photo_realistic.png" (for REALISTIC). */
    public String keyFor(String originalKey) {
        Objects.requireNonNull(originalKey, "originalKey must not be null");
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
            default -> throw new IllegalStateException("Unhandled StyleVariation: " + this);
        }
    }
}
