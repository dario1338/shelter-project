package org.unibl.etf.shelterapi.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.config.LoginThrottleProperties;
import org.unibl.etf.shelterapi.exception.TooManyAttemptsException;
import org.unibl.etf.shelterapi.model.LoginAttempt;
import org.unibl.etf.shelterapi.repository.LoginAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class LoginThrottleService {

    private static final int SAVE_ATTEMPTS = 2;

    private final LoginAttemptRepository repository;
    private final LoginThrottleProperties properties;
    private final Clock clock;

    public LoginThrottleService(LoginAttemptRepository repository, LoginThrottleProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Throws while the account is locked. Works the same for emails that do not exist. */
    public void checkAllowed(String email) {
        Instant now = clock.instant();
        repository.findById(Hashing.sha256Hex(email))
                .map(LoginAttempt::getLockedUntil)
                .filter(lockedUntil -> lockedUntil.isAfter(now))
                .ifPresent(lockedUntil -> {
                    throw new TooManyAttemptsException(Math.max(1L, Duration.between(now, lockedUntil).toSeconds()));
                });
    }

    public void recordFailure(String email) {
        String key = Hashing.sha256Hex(email);
        for (int attempt = 0; attempt < SAVE_ATTEMPTS; attempt++) {
            try {
                saveFailure(key);
                return;
            } catch (DataIntegrityViolationException e) {
                // Two first failures for the same email were saved at once; the second one retries as an update.
            }
        }
    }

    public void reset(String email) {
        repository.deleteById(Hashing.sha256Hex(email));
    }

    @Transactional
    public void deleteStale() {
        Instant now = clock.instant();
        repository.deleteStale(now.minus(properties.window()), now);
    }

    private void saveFailure(String key) {
        Instant now = clock.instant();
        LoginAttempt attempt = repository.findById(key).orElseGet(() -> new LoginAttempt(key, now));
        if (attempt.getWindowStart().plus(properties.window()).isBefore(now)) {
            attempt.restartWindow(now);
        }
        attempt.increment();
        if (attempt.getFailedCount() >= properties.maxAttempts()) {
            attempt.lockUntil(now.plus(properties.lockDuration()));
        }
        repository.save(attempt);
    }
}