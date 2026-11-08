package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExifOrientationTest {

    @Test
    void jpegWithoutExifNeedsNoTurn() {
        assertEquals(1, ExifOrientation.read(TestImages.jpeg(8, 8)));
    }

    @Test
    void readsLittleEndianOrientation() {
        byte[] file = TestImages.withOrientation(TestImages.jpeg(8, 8), 6, true);

        assertEquals(6, ExifOrientation.read(file));
    }

    @Test
    void readsBigEndianOrientation() {
        byte[] file = TestImages.withOrientation(TestImages.jpeg(8, 8), 8, false);

        assertEquals(8, ExifOrientation.read(file));
    }

    @Test
    void valueOutsideTheRangeIsIgnored() {
        byte[] file = TestImages.withOrientation(TestImages.jpeg(8, 8), 9, true);

        assertEquals(1, ExifOrientation.read(file));
    }

    @Test
    void dataThatIsNotAJpegNeedsNoTurn() {
        assertEquals(1, ExifOrientation.read(new byte[0]));
        assertEquals(1, ExifOrientation.read(new byte[]{1, 2, 3, 4, 5, 6}));
        assertEquals(1, ExifOrientation.read(TestImages.png(8, 8)));
    }

    @Test
    void truncatedExifBlockNeedsNoTurn() {
        byte[] file = TestImages.withOrientation(TestImages.jpeg(8, 8), 6, true);
        byte[] truncated = java.util.Arrays.copyOf(file, 20);

        assertEquals(1, ExifOrientation.read(truncated));
    }
}
