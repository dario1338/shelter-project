package org.unibl.etf.shelterapi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.config.RefreshTokenProperties;
import org.unibl.etf.shelterapi.exception.InvalidRefreshTokenException;
import org.unibl.etf.shelterapi.model.RefreshToken;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.RefreshTokenRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    public record Rotation(User user, String refreshToken) {
    }

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new token family (a new login) and returns the raw token, which is never stored. */
    @Transactional
    public String issue(User user) {
        return createToken(user, UUID.randomUUID(), clock.instant());
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family. A token that was already used
     * is a sign of theft or a replayed request, so the whole family is revoked. That revocation has to
     * survive the exception, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(Hashing.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        boolean reused = current.getUsedAt() != null || repository.markUsed(current.getId(), now) == 0;
        if (reused) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        String next = createToken(current.getUser(), current.getFamilyId(), now);
        return new Rotation(current.getUser(), next);
    }

    /** Logout: revokes the whole family of the presented token. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        Instant now = clock.instant();
        repository.findByTokenHash(Hashing.sha256Hex(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), now));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    @Transactional
    public void deleteExpired() {
        repository.deleteExpiredBefore(clock.instant());
    }

    private String createToken(User user, UUID familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(
                user, Hashing.sha256Hex(raw), familyId, now.plus(properties.expiration()), now));
        return raw;
    }
}