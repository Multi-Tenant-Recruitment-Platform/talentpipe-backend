package com.talentpipe.common.util;

import java.time.Duration;

/**
 * Platform-wide brute-force policy: after {@value #MAX_FAILED_ATTEMPTS}
 * consecutive failed logins an account is locked for
 * {@code LOCK_DURATION}. Shared by both identities — company users and
 * candidates — so the two login paths cannot drift apart.
 */
public final class LockoutPolicy {

    /** Consecutive failures that trigger a lock. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** How long a locked account stays locked. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LockoutPolicy() {
        // constants only
    }
}
