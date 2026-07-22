package com.talentpipe.candidate.service;

import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.util.LockoutPolicy;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate counterpart of {@link com.talentpipe.auth.service.LoginAttemptService}.
 *
 * <p>Same reasoning: candidate login also runs inside a transaction that rolls
 * back on failure, so the failed-attempt increment must be committed in its own
 * {@code REQUIRES_NEW} transaction or the lock never engages. Separate bean so
 * the {@code REQUIRES_NEW} boundary is crossed through the Spring proxy rather
 * than a self-invocation (which would silently ignore the propagation).</p>
 */
@Service
public class CandidateLoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(CandidateLoginAttemptService.class);

    private final CandidateRepository candidateRepository;

    public CandidateLoginAttemptService(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    /** Increments the failure counter, locking the candidate once it hits the threshold. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID candidateId) {
        candidateRepository.findById(candidateId).ifPresent(candidate -> {
            if (candidate.registerFailedLogin(Instant.now())) {
                log.warn("Candidate {} locked for {} after {} failed login attempts",
                        candidateId, LockoutPolicy.LOCK_DURATION, LockoutPolicy.MAX_FAILED_ATTEMPTS);
            }
        });
    }

    /** Clears the counter and any lapsed lock after a success, in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID candidateId) {
        candidateRepository.findById(candidateId).ifPresent(Candidate::registerSuccessfulLogin);
    }
}
