package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.unibl.etf.shelterapi.model.LoginAttempt;
import org.unibl.etf.shelterapi.model.RefreshToken;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.LoginAttemptRepository;
import org.unibl.etf.shelterapi.repository.RefreshTokenRepository;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class RefreshTokenRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private LoginAttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
        attemptRepository.deleteAll();
    }

    private User saveUser() {
        return userRepository.save(new User(UUID.randomUUID() + "@example.com", "hashed", "Ana", "Anic", Role.USER));
    }

    private RefreshToken saveToken(User user, UUID family, Instant expiresAt) {
        return tokenRepository.save(new RefreshToken(user, UUID.randomUUID().toString(), family, expiresAt, now));
    }

    @Test
    void findByTokenHashLoadsTheOwner() {
        User user = saveUser();
        RefreshToken token = saveToken(user, UUID.randomUUID(), now.plusSeconds(600));

        RefreshToken found = tokenRepository.findByTokenHash(token.getTokenHash()).orElseThrow();

        assertEquals(user.getEmail(), found.getUser().getEmail());
    }

    @Test
    void aTokenCanBeMarkedUsedOnlyOnce() {
        RefreshToken token = saveToken(saveUser(), UUID.randomUUID(), now.plusSeconds(600));

        assertEquals(1, tokenRepository.markUsed(token.getId(), now));
        assertEquals(0, tokenRepository.markUsed(token.getId(), now));
        assertNotNull(tokenRepository.findById(token.getId()).orElseThrow().getUsedAt());
    }

    @Test
    void revokingAFamilyLeavesOtherFamiliesAlone() {
        User user = saveUser();
        UUID family = UUID.randomUUID();
        RefreshToken first = saveToken(user, family, now.plusSeconds(600));
        RefreshToken second = saveToken(user, family, now.plusSeconds(600));
        RefreshToken other = saveToken(user, UUID.randomUUID(), now.plusSeconds(600));

        assertEquals(2, tokenRepository.revokeFamily(family, now));

        assertNotNull(tokenRepository.findById(first.getId()).orElseThrow().getRevokedAt());
        assertNotNull(tokenRepository.findById(second.getId()).orElseThrow().getRevokedAt());
        assertNull(tokenRepository.findById(other.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void revokingAllTokensOfAUserLeavesOtherUsersAlone() {
        User ana = saveUser();
        User marko = saveUser();
        saveToken(ana, UUID.randomUUID(), now.plusSeconds(600));
        saveToken(ana, UUID.randomUUID(), now.plusSeconds(600));
        RefreshToken markos = saveToken(marko, UUID.randomUUID(), now.plusSeconds(600));

        assertEquals(2, tokenRepository.revokeAllForUser(ana.getId(), now));

        assertNull(tokenRepository.findById(markos.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void deletesOnlyExpiredTokens() {
        User user = saveUser();
        saveToken(user, UUID.randomUUID(), now.minusSeconds(10));
        RefreshToken valid = saveToken(user, UUID.randomUUID(), now.plusSeconds(600));

        assertEquals(1, tokenRepository.deleteExpiredBefore(now));

        assertEquals(1, tokenRepository.count());
        assertTrue(tokenRepository.findById(valid.getId()).isPresent());
    }

    @Test
    void tokenHashesAreUnique() {
        User user = saveUser();
        RefreshToken first = saveToken(user, UUID.randomUUID(), now.plusSeconds(600));
        RefreshToken duplicate = new RefreshToken(user, first.getTokenHash(), UUID.randomUUID(), now.plusSeconds(600), now);

        assertThrows(DataIntegrityViolationException.class, () -> tokenRepository.saveAndFlush(duplicate));
    }

    @Test
    void deleteStaleKeepsActiveCountersAndActiveLocks() {
        attemptRepository.save(new LoginAttempt("old", now.minusSeconds(7200)));
        attemptRepository.save(new LoginAttempt("recent", now.minusSeconds(60)));
        LoginAttempt locked = new LoginAttempt("locked", now.minusSeconds(7200));
        locked.lockUntil(now.plusSeconds(600));
        attemptRepository.save(locked);

        assertEquals(1, attemptRepository.deleteStale(now.minusSeconds(900), now));

        assertTrue(attemptRepository.findById("recent").isPresent());
        assertTrue(attemptRepository.findById("locked").isPresent());
    }
}