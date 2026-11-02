package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.exception.InvalidRefreshTokenException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final String ACCESS = "access-token";
    private static final String REFRESH = "refresh-token";

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private SessionService sessionService;

    private final User user = new User("ana@example.com", "hash", "Ana", "Anic", Role.USER);

    private void stubAccessToken() {
        when(jwtService.generateToken(user)).thenReturn(ACCESS);
        when(jwtService.getExpiresInSeconds()).thenReturn(900L);
    }

    @Test
    void loginReturnsAccessTokenAndNewRefreshToken() {
        LoginRequest request = new LoginRequest("ana@example.com", "strong-pass-1");
        when(authService.authenticate(request)).thenReturn(user);
        when(refreshTokenService.issue(user)).thenReturn(REFRESH);
        stubAccessToken();

        SessionService.Session session = sessionService.login(request);

        assertEquals(ACCESS, session.auth().accessToken());
        assertEquals("Bearer", session.auth().tokenType());
        assertEquals(900L, session.auth().expiresIn());
        assertEquals("USER", session.auth().role());
        assertEquals(REFRESH, session.refreshToken());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAccessToken() {
        when(refreshTokenService.rotate("old")).thenReturn(new RefreshTokenService.Rotation(user, REFRESH));
        stubAccessToken();

        SessionService.Session session = sessionService.refresh("old");

        assertEquals(ACCESS, session.auth().accessToken());
        assertEquals(REFRESH, session.refreshToken());
    }

    @Test
    void refreshRejectsAMissingOrBlankToken() {
        assertThrows(InvalidRefreshTokenException.class, () -> sessionService.refresh(null));
        assertThrows(InvalidRefreshTokenException.class, () -> sessionService.refresh("  "));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void logoutRevokesThePresentedToken() {
        sessionService.logout(REFRESH);

        verify(refreshTokenService).revoke(REFRESH);
    }

    @Test
    void logoutWithoutATokenDoesNothing() {
        sessionService.logout(null);

        verifyNoInteractions(refreshTokenService);
    }
}