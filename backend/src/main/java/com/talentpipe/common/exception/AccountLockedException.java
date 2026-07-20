package com.talentpipe.common.exception;

import java.time.Duration;
import java.time.Instant;

/**
 * Thrown when a login is attempted against a temporarily locked account
 * (brute-force protection: 5 failed attempts → 15-minute lock). Maps to
 * HTTP 403 with explicit retry timing — the lock only ever engages for an
 * account that exists and has been actively attacked, and the owner needs to
 * know when they can try again.
 */
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil, Instant now) {
        super("Account temporarily locked due to repeated failed login attempts. "
                + "Try again in " + remainingMinutes(lockedUntil, now) + " minute(s).");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    private static long remainingMinutes(Instant lockedUntil, Instant now) {
        // Round up so "30 seconds left" reads as 1 minute, never 0.
        long seconds = Math.max(1, Duration.between(now, lockedUntil).getSeconds());
        return (seconds + 59) / 60;
    }
}
