package org.unibl.etf.shelterapi.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Builds small image files for tests. */
public final class TestImages {

    private TestImages() {
    }

    public static BufferedImage filled(int width, int height, Color color, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    public static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] jpeg(int width, int height) {
        return encode(filled(width, height, Color.BLUE, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(filled(width, height, Color.GREEN, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static BufferedImage decode(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Inserts an EXIF block with the given orientation right after the start of the JPEG file. */
    public static byte[] withOrientation(byte[] jpeg, int orientation, boolean littleEndian) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(littleEndian ? new byte[]{'I', 'I'} : new byte[]{'M', 'M'});
        writeShort(tiff, 42, littleEndian);
        writeInt(tiff, 8, littleEndian);
        writeShort(tiff, 1, littleEndian);
        writeShort(tiff, 0x0112, littleEndian);
        writeShort(tiff, 3, littleEndian);
        writeInt(tiff, 1, littleEndian);
        writeShort(tiff, orientation, littleEndian);
        writeShort(tiff, 0, littleEndian);
        writeInt(tiff, 0, littleEndian);

        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + exifHeader.length + tiff.size();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(jpeg, 0, 2);
        result.write(0xFF);
        result.write(0xE1);
        result.write(length >> 8);
        result.write(length & 0xFF);
        result.writeBytes(exifHeader);
        result.writeBytes(tiff.toByteArray());
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }

    /** A PNG header that claims the given size, followed by a bogus data chunk. It is never decoded. */
    public static byte[] pngClaiming(int width, int height) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width, false);
        writeInt(header, height, false);
        header.writeBytes(new byte[]{8, 2, 0, 0, 0});
        writeChunk(bytes, "IHDR", header.toByteArray());
        writeChunk(bytes, "IDAT", new byte[]{1, 2, 3, 4});
        writeChunk(bytes, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length, false);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue(), false);
    }

    private static void writeShort(ByteArrayOutputStream out, int value, boolean littleEndian) {
        if (littleEndian) {
            out.write(value & 0xFF);
            out.write(value >> 8);
        } else {
            out.write(value >> 8);
            out.write(value & 0xFF);
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int value, boolean littleEndian) {
        if (littleEndian) {
            writeShort(out, value & 0xFFFF, true);
            writeShort(out, value >>> 16, true);
        } else {
            writeShort(out, value >>> 16, false);
            writeShort(out, value & 0xFFFF, false);
        }
    }
}
