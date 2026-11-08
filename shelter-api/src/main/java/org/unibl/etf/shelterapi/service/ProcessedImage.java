package org.unibl.etf.shelterapi.service;

/** An uploaded photo after validation and re-encoding. */
public final class ProcessedImage {

    private final byte[] content;
    private final String contentType;
    private final String extension;

    public ProcessedImage(byte[] content, String contentType, String extension) {
        this.content = content;
        this.contentType = contentType;
        this.extension = extension;
    }

    public byte[] content() {
        return content;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
