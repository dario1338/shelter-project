package org.unibl.etf.shelterapi.exception;

public class ForbiddenOriginException extends RuntimeException {

    public ForbiddenOriginException() {
        super("Origin is not allowed");
    }
}