package org.unibl.etf.shelterapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.refresh")
public record RefreshTokenProperties(
        @DefaultValue("7d") Duration expiration,
        @DefaultValue("true") boolean cookieSecure
) {

    public RefreshTokenProperties {
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("app.security.refresh.expiration must be positive");
        }
    }
}
