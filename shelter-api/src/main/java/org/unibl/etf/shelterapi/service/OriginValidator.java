package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Component;
import org.unibl.etf.shelterapi.config.CsrfProperties;
import org.unibl.etf.shelterapi.exception.ForbiddenOriginException;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Browsers always send the Origin header on cross-site POST requests and it cannot be forged by page
 * scripts. Cookie-based endpoints accept only the configured origins; a missing Origin is rejected too.
 */
@Component
public class OriginValidator {

    private final Set<String> allowedOrigins;

    public OriginValidator(CsrfProperties properties) {
        this.allowedOrigins = properties.allowedOrigins().stream()
                .map(OriginValidator::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void requireAllowed(String origin) {
        if (origin == null || !allowedOrigins.contains(normalize(origin))) {
            throw new ForbiddenOriginException();
        }
    }

    private static String normalize(String origin) {
        String value = origin.trim().toLowerCase(Locale.ROOT);
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}