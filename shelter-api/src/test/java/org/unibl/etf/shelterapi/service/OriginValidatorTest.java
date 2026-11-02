package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.unibl.etf.shelterapi.config.CsrfProperties;
import org.unibl.etf.shelterapi.exception.ForbiddenOriginException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OriginValidatorTest {

    private static final String APP = "https://app.example.com";

    private final OriginValidator validator =
            new OriginValidator(new CsrfProperties(List.of(APP + "/", "http://localhost:5173")));

    @Test
    void acceptsConfiguredOrigins() {
        assertDoesNotThrow(() -> validator.requireAllowed(APP));
        assertDoesNotThrow(() -> validator.requireAllowed("http://localhost:5173"));
    }

    @Test
    void ignoresCaseAndTrailingSlash() {
        assertDoesNotThrow(() -> validator.requireAllowed("HTTPS://APP.example.com/"));
    }

    @Test
    void rejectsAMissingOrigin() {
        assertThrows(ForbiddenOriginException.class, () -> validator.requireAllowed(null));
    }

    @Test
    void rejectsAnotherOrigin() {
        assertThrows(ForbiddenOriginException.class, () -> validator.requireAllowed("https://evil.example"));
    }

    @Test
    void rejectsAnEmptyConfiguration() {
        List<String> blankOrigins = List.of(" ");

        assertThrows(IllegalArgumentException.class, () -> new CsrfProperties(blankOrigins));
    }
}