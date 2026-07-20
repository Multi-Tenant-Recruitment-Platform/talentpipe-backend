package com.talentpipe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the credential endpoints to 10 requests per minute per client IP,
 * answering 429 with the standard error envelope once the limit is passed.
 *
 * <p>Complements the per-account lockout: lockout stops an attacker grinding
 * one account, this stops them spraying one password across many accounts, and
 * it also caps abuse of the password-reset and resend-verification endpoints,
 * which send mail.</p>
 *
 * <p>Runs before authentication so rejected requests never reach a password
 * comparison. The limit is per application instance — see {@link RateLimiter}.</p>
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    /** Endpoints that either check credentials or trigger an email. */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/resend-verification");

    private final RateLimiter rateLimiter =
            new RateLimiter(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
    private final SecurityErrorWriter errorWriter;

    public LoginRateLimitFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = clientIp(request);
        Instant now = Instant.now();

        if (!rateLimiter.tryAcquire(clientIp, now)) {
            long retryAfter = rateLimiter.secondsUntilReset(clientIp, now);
            log.warn("Rate limit exceeded for {} on {}", clientIp, request.getRequestURI());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Try again in " + retryAfter + " second(s).");
            return; // stop the chain — the request never reaches the controller
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Uses the socket address, not {@code X-Forwarded-For}: that header is
     * caller-supplied and trivially spoofed, which would let an attacker mint a
     * fresh bucket per request. Behind a trusted reverse proxy, configure
     * {@code server.forward-headers-strategy=NATIVE} so the container resolves
     * the real client address into {@code getRemoteAddr()} instead.
     */
    private static String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
