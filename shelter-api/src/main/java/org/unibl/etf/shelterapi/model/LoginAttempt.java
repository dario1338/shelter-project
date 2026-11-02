package org.unibl.etf.shelterapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected LoginAttempt() {
        // required by JPA
    }

    public LoginAttempt(String emailHash, Instant windowStart) {
        this.emailHash = emailHash;
        this.windowStart = windowStart;
    }

    public void restartWindow(Instant now) {
        this.windowStart = now;
        this.failedCount = 0;
        this.lockedUntil = null;
    }

    public void increment() {
        this.failedCount++;
    }

    public void lockUntil(Instant instant) {
        this.lockedUntil = instant;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}