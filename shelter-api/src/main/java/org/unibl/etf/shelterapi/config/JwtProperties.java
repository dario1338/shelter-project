package org.unibl.etf.shelterapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration expiration) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
    }
}
