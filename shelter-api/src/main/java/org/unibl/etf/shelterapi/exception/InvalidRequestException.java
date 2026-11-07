package org.unibl.etf.shelterapi.exception;

/** The request is well formed, but its content breaks a rule that annotations cannot express. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
