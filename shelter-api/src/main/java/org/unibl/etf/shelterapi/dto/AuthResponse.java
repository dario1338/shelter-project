package org.unibl.etf.shelterapi.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String role
) {
}
