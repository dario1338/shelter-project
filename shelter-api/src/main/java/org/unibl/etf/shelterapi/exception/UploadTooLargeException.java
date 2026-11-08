package org.unibl.etf.shelterapi.exception;

/** The uploaded file is bigger than the allowed size. */
public class UploadTooLargeException extends RuntimeException {

    public UploadTooLargeException(String message) {
        super(message);
    }
}
