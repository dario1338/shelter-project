package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Component;
import org.unibl.etf.shelterapi.exception.InvalidRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Turns an uploaded file into a safe photo. The file is accepted by its content, never by its name or by the
 * type the browser claims: it must decode as JPEG or PNG. It is then decoded, turned upright, scaled down and
 * encoded again, so nothing from the original file is stored (EXIF data such as the GPS position of the photo,
 * embedded thumbnails, trailing bytes or a payload hidden after the image).
 */
@Component
public class ImageProcessor {

    static final int MAX_DIMENSION = 1600;
    /** Guards against files that are small to upload but huge once decoded. */
    static final long MAX_PIXELS = 40_000_000L;

    private static final float JPEG_QUALITY = 0.85f;
    private static final String JPEG = "jpeg";
    private static final String PNG = "png";
    private static final String INVALID_IMAGE = "The file is not a valid JPEG or PNG image";

    public ProcessedImage process(byte[] input) {
        String format = detectFormat(input);
        BufferedImage decoded = decode(input, format);

        BufferedImage image = scaleDown(decoded, format);
        if (JPEG.equals(format)) {
            image = orient(image, ExifOrientation.read(input));
        }
        byte[] encoded = encode(image, format);
        return JPEG.equals(format)
                ? new ProcessedImage(encoded, "image/jpeg", "jpg")
                : new ProcessedImage(encoded, "image/png", "png");
    }

    private static String detectFormat(byte[] input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidRequestException(INVALID_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!JPEG.equals(format) && !PNG.equals(format)) {
                    throw new InvalidRequestException(INVALID_IMAGE);
                }
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidRequestException(INVALID_IMAGE);
        }
    }

    private static BufferedImage decode(byte[] input, String format) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new InvalidRequestException("The image is too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidRequestException(INVALID_IMAGE);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidRequestException(INVALID_IMAGE);
        }
    }

    /** Scales the longest side down to MAX_DIMENSION, halving first so that a big reduction stays smooth. */
    private static BufferedImage scaleDown(BufferedImage source, String format) {
        boolean alpha = PNG.equals(format) && source.getColorModel().hasAlpha();
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        if (current.getType() != type) {
            current = redraw(current, current.getWidth(), current.getHeight(), type);
        }
        while (Math.max(current.getWidth(), current.getHeight()) > MAX_DIMENSION) {
            double scale = Math.max(MAX_DIMENSION / (double) Math.max(current.getWidth(), current.getHeight()), 0.5);
            int width = Math.max(1, (int) Math.round(current.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(current.getHeight() * scale));
            current = redraw(current, width, height, type);
        }
        return current;
    }

    private static BufferedImage redraw(BufferedImage source, int width, int height, int type) {
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Applies the EXIF orientation value (1 to 8), so that the stored photo is upright without any tag. */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height, source.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] destination = destination(orientation, x, y, width, height);
                target.setRGB(destination[0], destination[1], source.getRGB(x, y));
            }
        }
        return target;
    }

    private static int[] destination(int orientation, int x, int y, int width, int height) {
        return switch (orientation) {
            case 2 -> new int[]{width - 1 - x, y};
            case 3 -> new int[]{width - 1 - x, height - 1 - y};
            case 4 -> new int[]{x, height - 1 - y};
            case 5 -> new int[]{y, x};
            case 6 -> new int[]{height - 1 - y, x};
            case 7 -> new int[]{height - 1 - y, width - 1 - x};
            default -> new int[]{y, width - 1 - x};
        };
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (JPEG.equals(format)) {
                writeJpeg(image, bytes);
            } else if (!ImageIO.write(image, PNG, bytes)) {
                throw new IOException("No PNG writer available");
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new InvalidRequestException(INVALID_IMAGE);
        }
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream bytes) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(JPEG).next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }
}
