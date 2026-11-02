package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Service;
import org.unibl.etf.shelterapi.dto.AuthResponse;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.exception.InvalidRefreshTokenException;
import org.unibl.etf.shelterapi.model.User;

@Service
public class SessionService {

    private static final String TOKEN_TYPE = "Bearer";

    /** The access token goes to the response body, the refresh token to the cookie. */
    public record Session(AuthResponse auth, String refreshToken) {
    }

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public SessionService(AuthService authService, RefreshTokenService refreshTokenService, JwtService jwtService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    public Session login(LoginRequest request) {
        User user = authService.authenticate(request);
        return new Session(toAuthResponse(user), refreshTokenService.issue(user));
    }

    public Session refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return new Session(toAuthResponse(rotation.user()), rotation.refreshToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                TOKEN_TYPE,
                jwtService.getExpiresInSeconds(),
                user.getRole().name());
    }
}