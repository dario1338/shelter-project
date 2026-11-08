package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static boolean contains(byte[] data, String text) {
        byte[] needle = text.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i + needle.length <= data.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(data, i, i + needle.length), needle)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void jpegIsStoredAsJpeg() {
        ProcessedImage result = processor.process(TestImages.jpeg(40, 30));

        assertEquals("image/jpeg", result.contentType());
        assertEquals("jpg", result.extension());
        BufferedImage decoded = TestImages.decode(result.content());
        assertEquals(40, decoded.getWidth());
        assertEquals(30, decoded.getHeight());
    }

    @Test
    void pngIsStoredAsPng() {
        ProcessedImage result = processor.process(TestImages.png(40, 30));

        assertEquals("image/png", result.contentType());
        assertEquals("png", result.extension());
        assertEquals(40, TestImages.decode(result.content()).getWidth());
    }

    @Test
    void pngKeepsTransparency() {
        BufferedImage source = TestImages.filled(10, 10, new Color(0, 0, 0, 0), BufferedImage.TYPE_INT_ARGB);
        source.setRGB(5, 5, new Color(255, 0, 0, 255).getRGB());

        ProcessedImage result = processor.process(TestImages.encode(source, "png"));

        BufferedImage decoded = TestImages.decode(result.content());
        assertEquals(0, decoded.getRGB(1, 1) >>> 24);
        assertEquals(255, decoded.getRGB(5, 5) >>> 24);
    }

    @Test
    void largePhotoIsScaledDownKeepingTheProportions() {
        ProcessedImage result = processor.process(TestImages.jpeg(3200, 1600));

        BufferedImage decoded = TestImages.decode(result.content());
        assertEquals(ImageProcessor.MAX_DIMENSION, decoded.getWidth());
        assertEquals(800, decoded.getHeight());
    }

    @Test
    void tallPhotoIsScaledDownByItsHeight() {
        ProcessedImage result = processor.process(TestImages.png(1000, 4000));

        BufferedImage decoded = TestImages.decode(result.content());
        assertEquals(ImageProcessor.MAX_DIMENSION, decoded.getHeight());
        assertEquals(400, decoded.getWidth());
    }

    @Test
    void exifDataIsRemoved() {
        byte[] original = TestImages.withOrientation(TestImages.jpeg(20, 20), 1, true);
        assertTrue(contains(original, "Exif"));

        ProcessedImage result = processor.process(original);

        assertFalse(contains(result.content(), "Exif"));
    }

    @Test
    void bytesAfterTheImageAreNotKept() {
        byte[] original = TestImages.jpeg(20, 20);
        byte[] polyglot = Arrays.copyOf(original, original.length + 16);
        byte[] secret = "SECRET-PAYLOAD!!".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(secret, 0, polyglot, original.length, secret.length);

        ProcessedImage result = processor.process(polyglot);

        assertFalse(contains(result.content(), "SECRET-PAYLOAD"));
    }

    @Test
    void photoIsTurnedUprightAccordingToItsOrientation() {
        // 40 x 20 photo with a red square in the top left corner, stored sideways (orientation 6).
        BufferedImage source = TestImages.filled(40, 20, Color.BLUE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                source.setRGB(x, y, Color.RED.getRGB());
            }
        }
        byte[] file = TestImages.withOrientation(TestImages.encode(source, "jpg"), 6, true);

        BufferedImage result = TestImages.decode(processor.process(file).content());

        assertEquals(20, result.getWidth());
        assertEquals(40, result.getHeight());
        Color topRight = new Color(result.getRGB(15, 5));
        Color topLeft = new Color(result.getRGB(5, 5));
        assertTrue(topRight.getRed() > 150 && topRight.getBlue() < 100);
        assertTrue(topLeft.getBlue() > 150 && topLeft.getRed() < 100);
    }

    /** Pixels of a 3 x 2 image A B C / D E F are numbered 1 to 6, row by row. */
    private static List<String> turned(int orientation) {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                source.setRGB(x, y, y * 3 + x + 1);
            }
        }
        BufferedImage result = ImageProcessor.orient(source, orientation);
        List<String> rows = new ArrayList<>();
        for (int y = 0; y < result.getHeight(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < result.getWidth(); x++) {
                row.append((char) ('A' + (result.getRGB(x, y) & 0xFFFFFF) - 1));
            }
            rows.add(row.toString());
        }
        return rows;
    }

    @Test
    void everyOrientationMovesThePixelsAsTheExifSpecificationDescribes() {
        assertEquals(List.of("ABC", "DEF"), turned(1));
        assertEquals(List.of("CBA", "FED"), turned(2));
        assertEquals(List.of("FED", "CBA"), turned(3));
        assertEquals(List.of("DEF", "ABC"), turned(4));
        assertEquals(List.of("AD", "BE", "CF"), turned(5));
        assertEquals(List.of("DA", "EB", "FC"), turned(6));
        assertEquals(List.of("FC", "EB", "DA"), turned(7));
        assertEquals(List.of("CF", "BE", "AD"), turned(8));
    }

    @Test
    void textFileIsRejected() {
        byte[] text = "hello, I am not an image".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidRequestException.class, () -> processor.process(text));
    }

    @Test
    void emptyFileIsRejected() {
        byte[] empty = new byte[0];

        assertThrows(InvalidRequestException.class, () -> processor.process(empty));
    }

    @Test
    void otherImageFormatsAreRejectedEvenWhenTheyAreValid() {
        byte[] gif = TestImages.encode(TestImages.filled(10, 10, Color.RED, BufferedImage.TYPE_INT_RGB), "gif");

        assertThrows(InvalidRequestException.class, () -> processor.process(gif));
    }

    @Test
    void damagedJpegIsRejected() {
        byte[] original = TestImages.jpeg(200, 200);
        byte[] damaged = Arrays.copyOf(original, 120);

        assertThrows(InvalidRequestException.class, () -> processor.process(damaged));
    }

    @Test
    void imageWithTooManyPixelsIsRejectedBeforeItIsDecoded() {
        byte[] bomb = TestImages.pngClaiming(20_000, 20_000);

        InvalidRequestException error = assertThrows(InvalidRequestException.class, () -> processor.process(bomb));

        assertEquals("The image is too large", error.getMessage());
    }
}
