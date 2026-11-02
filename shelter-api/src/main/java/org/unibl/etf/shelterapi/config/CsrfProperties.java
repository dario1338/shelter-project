package org.unibl.etf.shelterapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.csrf")
public record CsrfProperties(List<String> allowedOrigins) {

    public CsrfProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.security.csrf.allowed-origins must contain at least one origin");
        }
    }
}