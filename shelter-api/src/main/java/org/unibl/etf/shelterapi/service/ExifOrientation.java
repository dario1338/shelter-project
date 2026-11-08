package org.unibl.etf.shelterapi.service;

/**
 * Reads the orientation tag from the EXIF block of a JPEG file. Cameras and phones store a photo as the sensor
 * saw it and record in this tag how it has to be turned for display. The photo is re-encoded without metadata,
 * so the turn has to be applied before the tag is lost.
 */
final class ExifOrientation {

    static final int NORMAL = 1;

    private static final int MARKER_PREFIX = 0xFF;
    private static final int MARKER_SOI = 0xD8;
    private static final int MARKER_EOI = 0xD9;
    private static final int MARKER_SOS = 0xDA;
    private static final int MARKER_APP1 = 0xE1;
    private static final int MARKER_TEM = 0x01;
    private static final int MARKER_RST_FIRST = 0xD0;
    private static final int MARKER_RST_LAST = 0xD7;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final int ORIENTATION_TAG = 0x0112;
    private static final int TYPE_SHORT = 3;
    private static final int ENTRY_SIZE = 12;
    private static final int TIFF_MAGIC = 42;
    private static final int MAX_ENTRIES = 1000;

    private ExifOrientation() {
    }

    /** Returns a value from 1 to 8, or 1 (no turn needed) when the file has no usable orientation tag. */
    static int read(byte[] data) {
        if (data.length < 4 || unsigned(data[0]) != MARKER_PREFIX || unsigned(data[1]) != MARKER_SOI) {
            return NORMAL;
        }
        int pos = 2;
        while (pos + 4 <= data.length) {
            if (unsigned(data[pos]) != MARKER_PREFIX) {
                return NORMAL;
            }
            int marker = unsigned(data[pos + 1]);
            if (marker == MARKER_PREFIX) {
                pos++;
            } else if (isStandalone(marker)) {
                pos += 2;
            } else if (marker == MARKER_SOS || marker == MARKER_EOI) {
                return NORMAL;
            } else {
                int length = (unsigned(data[pos + 2]) << 8) | unsigned(data[pos + 3]);
                if (length < 2) {
                    return NORMAL;
                }
                if (marker == MARKER_APP1 && hasExifHeader(data, pos + 4)) {
                    return readTiff(data, pos + 4 + EXIF_HEADER.length, Math.min(pos + 2 + length, data.length));
                }
                pos += 2 + length;
            }
        }
        return NORMAL;
    }

    private static boolean isStandalone(int marker) {
        return marker == MARKER_SOI || marker == MARKER_TEM
                || (marker >= MARKER_RST_FIRST && marker <= MARKER_RST_LAST);
    }

    private static boolean hasExifHeader(byte[] data, int start) {
        if (start + EXIF_HEADER.length > data.length) {
            return false;
        }
        for (int i = 0; i < EXIF_HEADER.length; i++) {
            if (data[start + i] != EXIF_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readTiff(byte[] data, int tiffStart, int end) {
        if (tiffStart + 8 > end) {
            return NORMAL;
        }
        boolean little = data[tiffStart] == 'I' && data[tiffStart + 1] == 'I';
        boolean big = data[tiffStart] == 'M' && data[tiffStart + 1] == 'M';
        if ((!little && !big) || readShort(data, tiffStart + 2, little) != TIFF_MAGIC) {
            return NORMAL;
        }
        long ifdStart = tiffStart + (readInt(data, tiffStart + 4, little) & 0xFFFFFFFFL);
        if (ifdStart + 2 > end) {
            return NORMAL;
        }
        int entryCount = Math.min(readShort(data, (int) ifdStart, little), MAX_ENTRIES);
        for (int i = 0; i < entryCount; i++) {
            long entry = ifdStart + 2 + (long) i * ENTRY_SIZE;
            if (entry + ENTRY_SIZE > end) {
                return NORMAL;
            }
            int position = (int) entry;
            if (readShort(data, position, little) == ORIENTATION_TAG) {
                return readOrientationValue(data, position, little);
            }
        }
        return NORMAL;
    }

    private static int readOrientationValue(byte[] data, int entry, boolean little) {
        if (readShort(data, entry + 2, little) != TYPE_SHORT) {
            return NORMAL;
        }
        int value = readShort(data, entry + 8, little);
        return value >= 1 && value <= 8 ? value : NORMAL;
    }

    private static int readShort(byte[] data, int pos, boolean little) {
        int first = unsigned(data[pos]);
        int second = unsigned(data[pos + 1]);
        return little ? (second << 8) | first : (first << 8) | second;
    }

    private static int readInt(byte[] data, int pos, boolean little) {
        int first = readShort(data, pos, little);
        int second = readShort(data, pos + 2, little);
        return little ? (second << 16) | first : (first << 16) | second;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
