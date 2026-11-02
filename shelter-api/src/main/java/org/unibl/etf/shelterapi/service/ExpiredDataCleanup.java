package org.unibl.etf.shelterapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired refresh tokens and old login counters once a day. Deleting is idempotent, so any instance may run it. */
@Component
public class ExpiredDataCleanup {

    private final RefreshTokenService refreshTokenService;
    private final LoginThrottleService loginThrottleService;

    public ExpiredDataCleanup(RefreshTokenService refreshTokenService, LoginThrottleService loginThrottleService) {
        this.refreshTokenService = refreshTokenService;
        this.loginThrottleService = loginThrottleService;
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void run() {
        refreshTokenService.deleteExpired();
        loginThrottleService.deleteStale();
    }
}