package org.unibl.etf.shelterapi.service;

import java.util.Arrays;
import java.util.Objects;

/** An uploaded photo after validation and re-encoding. */
public record ProcessedImage(byte[] content, String contentType, String extension) {

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessedImage(byte[] otherContent, String otherType, String otherExtension)
                && Arrays.equals(content, otherContent)
                && Objects.equals(contentType, otherType)
                && Objects.equals(extension, otherExtension);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(contentType, extension) + Arrays.hashCode(content);
    }

    /** The bytes of the photo are left out, they are too long to be useful in a log. */
    @Override
    public String toString() {
        return "ProcessedImage[" + content.length + " bytes, " + contentType + ", " + extension + "]";
    }
}
