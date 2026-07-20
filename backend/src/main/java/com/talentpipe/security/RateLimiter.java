package com.talentpipe.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window rate limiter, keyed by an arbitrary string (client IP
 * for login).
 *
 * <p>Deliberately simple: at this scale a shared counter per key per window is
 * enough, and it costs one map lookup. The trade-off is that limits are
 * <strong>per instance</strong> — running several backend replicas multiplies
 * the effective limit, at which point this moves to Redis. Noted rather than
 * over-engineered now (SQS/Redis are later-sprint infrastructure).</p>
 *
 * <p>Windows are pruned lazily on write, so an idle key costs nothing and the
 * map cannot grow without bound from one-off IPs.</p>
 */
public class RateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * Records a request against {@code key}.
     *
     * @return true if the request is within the limit, false if it must be
     *         rejected with 429
     */
    public boolean tryAcquire(String key, Instant now) {
        pruneExpired(now);

        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.isExpired(now) ? new Window(now.plus(window)) : existing);

        return current.count.incrementAndGet() <= maxRequests;
    }

    /** When the caller's current window resets — drives the Retry-After hint. */
    public long secondsUntilReset(String key, Instant now) {
        Window current = windows.get(key);
        if (current == null || current.isExpired(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, current.expiresAt).getSeconds());
    }

    private void pruneExpired(Instant now) {
        // Cheap sweep: only touches entries that are already dead.
        windows.values().removeIf(existing -> existing.isExpired(now));
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
