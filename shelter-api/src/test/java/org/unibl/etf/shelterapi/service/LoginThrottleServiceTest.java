package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.unibl.etf.shelterapi.config.LoginThrottleProperties;
import org.unibl.etf.shelterapi.exception.TooManyAttemptsException;
import org.unibl.etf.shelterapi.model.LoginAttempt;
import org.unibl.etf.shelterapi.repository.LoginAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginThrottleServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String EMAIL = "ana@example.com";
    private static final String KEY = Hashing.sha256Hex(EMAIL);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(10);

    @Mock
    private LoginAttemptRepository repository;

    private LoginThrottleService service;

    @BeforeEach
    void setUp() {
        service = new LoginThrottleService(
                repository, new LoginThrottleProperties(3, WINDOW, LOCK), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LoginAttempt attempt(int failures, Instant windowStart) {
        LoginAttempt attempt = new LoginAttempt(KEY, windowStart);
        for (int i = 0; i < failures; i++) {
            attempt.increment();
        }
        return attempt;
    }

    private LoginAttempt savedAttempt() {
        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void allowsWhenThereAreNoFailures() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.checkAllowed(EMAIL));
    }

    @Test
    void blocksWhileLockedAndReportsRemainingSeconds() {
        LoginAttempt locked = attempt(3, NOW.minusSeconds(60));
        locked.lockUntil(NOW.plusSeconds(300));
        when(repository.findById(KEY)).thenReturn(Optional.of(locked));

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> service.checkAllowed(EMAIL));

        assertEquals(300, ex.getRetryAfterSeconds());
    }

    @Test
    void allowsAgainAfterTheLockExpired() {
        LoginAttempt expired = attempt(3, NOW.minusSeconds(900));
        expired.lockUntil(NOW.minusSeconds(1));
        when(repository.findById(KEY)).thenReturn(Optional.of(expired));

        assertDoesNotThrow(() -> service.checkAllowed(EMAIL));
    }

    @Test
    void firstFailureCreatesACounter() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());

        service.recordFailure(EMAIL);

        LoginAttempt saved = savedAttempt();
        assertEquals(1, saved.getFailedCount());
        assertEquals(NOW, saved.getWindowStart());
        assertNull(saved.getLockedUntil());
    }

    @Test
    void reachingTheLimitLocksTheAccount() {
        when(repository.findById(KEY)).thenReturn(Optional.of(attempt(2, NOW.minusSeconds(60))));

        service.recordFailure(EMAIL);

        LoginAttempt saved = savedAttempt();
        assertEquals(3, saved.getFailedCount());
        assertEquals(NOW.plus(LOCK), saved.getLockedUntil());
    }

    @Test
    void anExpiredWindowStartsCountingAgain() {
        when(repository.findById(KEY)).thenReturn(Optional.of(attempt(2, NOW.minus(WINDOW).minusSeconds(1))));

        service.recordFailure(EMAIL);

        LoginAttempt saved = savedAttempt();
        assertEquals(1, saved.getFailedCount());
        assertEquals(NOW, saved.getWindowStart());
        assertNull(saved.getLockedUntil());
    }

    @Test
    void retriesWhenTwoFirstFailuresAreSavedAtTheSameTime() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(repository.save(any(LoginAttempt.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recordFailure(EMAIL);

        verify(repository, times(2)).save(any(LoginAttempt.class));
    }

    @Test
    void resetDeletesTheCounter() {
        service.reset(EMAIL);

        verify(repository).deleteById(KEY);
    }

    @Test
    void deleteStaleRemovesOldCounters() {
        service.deleteStale();

        verify(repository).deleteStale(NOW.minus(WINDOW), NOW);
    }
}