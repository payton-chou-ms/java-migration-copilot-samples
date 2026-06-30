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
