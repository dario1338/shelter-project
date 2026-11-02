package org.unibl.etf.shelterapi.service;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.unibl.etf.shelterapi.config.RefreshTokenProperties;

import java.time.Duration;

@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    /** The browser sends the cookie only to the auth endpoints, never to the rest of the API. */
    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshTokenProperties properties;

    public RefreshCookieFactory(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(properties.expiration()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}