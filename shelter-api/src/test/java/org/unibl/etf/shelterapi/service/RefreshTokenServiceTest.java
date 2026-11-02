package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.unibl.etf.shelterapi.config.RefreshTokenProperties;
import org.unibl.etf.shelterapi.exception.InvalidRefreshTokenException;
import org.unibl.etf.shelterapi.model.RefreshToken;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.RefreshTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration LIFETIME = Duration.ofDays(7);
    private static final String RAW = "raw-refresh-token";
    private static final long TOKEN_ID = 7L;

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private final User user = new User("ana@example.com", "hash", "Ana", "Anic", Role.USER);

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(
                repository, new RefreshTokenProperties(LIFETIME, true), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RefreshToken stored(UUID family, Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, Hashing.sha256Hex(RAW), family, expiresAt, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(token, "id", TOKEN_ID);
        return token;
    }

    @Test
    void issueStoresOnlyTheHashOfTheToken() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertEquals(43, raw.length());
        assertEquals(Hashing.sha256Hex(raw), captor.getValue().getTokenHash());
        assertNotEquals(raw, captor.getValue().getTokenHash());
        assertEquals(NOW.plus(LIFETIME), captor.getValue().getExpiresAt());
    }

    @Test
    void rotateIssuesNewTokenInTheSameFamily() {
        UUID family = UUID.randomUUID();
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW)))
                .thenReturn(Optional.of(stored(family, NOW.plusSeconds(3600))));
        when(repository.markUsed(TOKEN_ID, NOW)).thenReturn(1);

        RefreshTokenService.Rotation rotation = service.rotate(RAW);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertSame(user, rotation.user());
        assertNotEquals(RAW, rotation.refreshToken());
        assertEquals(family, captor.getValue().getFamilyId());
        assertEquals(Hashing.sha256Hex(rotation.refreshToken()), captor.getValue().getTokenHash());
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW))).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshToken token = stored(UUID.randomUUID(), NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(token, "revokedAt", NOW.minusSeconds(10));
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW))).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        verify(repository, never()).markUsed(anyLong(), any(Instant.class));
    }

    @Test
    void rotateRejectsExpiredTokenWithoutRevokingTheFamily() {
        UUID family = UUID.randomUUID();
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW)))
                .thenReturn(Optional.of(stored(family, NOW.minusSeconds(1))));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        verify(repository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void rotateRevokesTheFamilyWhenAUsedTokenIsReplayed() {
        UUID family = UUID.randomUUID();
        RefreshToken token = stored(family, NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(token, "usedAt", NOW.minusSeconds(30));
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW))).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        verify(repository).revokeFamily(family, NOW);
        verify(repository, never()).markUsed(anyLong(), any(Instant.class));
    }

    @Test
    void rotateRevokesTheFamilyWhenTheTokenWasUsedConcurrently() {
        UUID family = UUID.randomUUID();
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW)))
                .thenReturn(Optional.of(stored(family, NOW.plusSeconds(3600))));
        when(repository.markUsed(TOKEN_ID, NOW)).thenReturn(0);

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        verify(repository).revokeFamily(family, NOW);
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokeRevokesTheWholeFamily() {
        UUID family = UUID.randomUUID();
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW)))
                .thenReturn(Optional.of(stored(family, NOW.plusSeconds(3600))));

        service.revoke(RAW);

        verify(repository).revokeFamily(family, NOW);
    }

    @Test
    void revokeIgnoresUnknownTokens() {
        when(repository.findByTokenHash(Hashing.sha256Hex(RAW))).thenReturn(Optional.empty());

        service.revoke(RAW);

        verify(repository, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void revokeAllForUserDelegatesToTheRepository() {
        service.revokeAllForUser(5L);

        verify(repository).revokeAllForUser(5L, NOW);
    }

    @Test
    void deleteExpiredRemovesTokensThatExpiredBeforeNow() {
        service.deleteExpired();

        verify(repository).deleteExpiredBefore(NOW);
    }
}