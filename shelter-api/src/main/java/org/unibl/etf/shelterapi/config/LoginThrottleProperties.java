package org.unibl.etf.shelterapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.login")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration window,
        @DefaultValue("15m") Duration lockDuration
) {

    public LoginThrottleProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.security.login.max-attempts must be at least 1");
        }
    }
}
