package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.util.LockoutPolicy;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the outcome of a login attempt for brute-force protection.
 *
 * <p><strong>Why this is a separate service with {@code REQUIRES_NEW}:</strong>
 * {@code AuthService.login} runs in a transaction that <em>rolls back</em> when
 * it throws "invalid credentials". If the failed-attempt increment lived in that
 * same transaction it would be rolled back along with it — so the counter would
 * reset to zero on every failure and the account could never lock. Committing the
 * increment in its own transaction makes it survive the caller's rollback, which
 * is the whole point of lockout.</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Increments the failure counter, locking the account once it hits the threshold. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.registerFailedLogin(Instant.now())) {
                log.warn("User {} locked for {} after {} failed login attempts",
                        userId, LockoutPolicy.LOCK_DURATION, LockoutPolicy.MAX_FAILED_ATTEMPTS);
            }
        });
    }

    /**
     * Clears the counter and any lapsed lock after a success. Runs in its own
     * transaction too, so a later failure in the login flow cannot undo the
     * reset (and vice versa).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        userRepository.findById(userId).ifPresent(User::registerSuccessfulLogin);
    }
}
