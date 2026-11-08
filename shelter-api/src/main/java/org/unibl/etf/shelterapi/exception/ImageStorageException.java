package org.unibl.etf.shelterapi.exception;

/** The object storage did not accept or return a file. The message is safe to show to the caller. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message) {
        super(message);
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
