package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessedImageTest {

    private static ProcessedImage image(byte[] content, String type) {
        return new ProcessedImage(content, type, "jpg");
    }

    @Test
    void imagesWithTheSameContentAreEqual() {
        ProcessedImage first = image(new byte[]{1, 2}, "image/jpeg");
        ProcessedImage second = image(new byte[]{1, 2}, "image/jpeg");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void imagesWithDifferentContentOrTypeAreNotEqual() {
        ProcessedImage original = image(new byte[]{1, 2}, "image/jpeg");

        assertNotEquals(original, image(new byte[]{1, 3}, "image/jpeg"));
        assertNotEquals(original, image(new byte[]{1, 2}, "image/png"));
        assertNotEquals(original, "not an image");
    }

    @Test
    void toStringShowsTheSizeAndNotTheBytes() {
        String text = image(new byte[]{1, 2, 3}, "image/jpeg").toString();

        assertTrue(text.contains("3 bytes"));
        assertTrue(text.contains("image/jpeg"));
    }
}
