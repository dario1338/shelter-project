package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiredDataCleanupTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginThrottleService loginThrottleService;

    @InjectMocks
    private ExpiredDataCleanup cleanup;

    @Test
    void removesExpiredTokensAndStaleCounters() {
        cleanup.run();

        verify(refreshTokenService).deleteExpired();
        verify(loginThrottleService).deleteStale();
    }
}