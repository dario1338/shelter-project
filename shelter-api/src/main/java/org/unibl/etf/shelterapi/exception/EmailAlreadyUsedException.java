package org.unibl.etf.shelterapi.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Email is already registered");
    }
}
