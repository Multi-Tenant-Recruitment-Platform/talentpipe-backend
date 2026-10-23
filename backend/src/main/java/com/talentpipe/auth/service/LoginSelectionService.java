package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.LoginSelection;
import com.talentpipe.auth.repository.LoginSelectionRepository;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.util.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems the token behind the "choose a workspace" step of
 * sign-in (see {@link AuthService#selectWorkspace}).
 *
 * <p>A separate bean so {@link #issue}'s {@code REQUIRES_NEW} boundary is
 * crossed through the Spring proxy: the sign-in that records the choice then
 * answers with an error, which rolls its own transaction back and would take
 * the new row with it.</p>
 */
@Service
public class LoginSelectionService {

    /** Long enough to read a short list and tap one, short enough to be worthless if it leaks. */
    static final Duration TTL = Duration.ofMinutes(2);

    private final LoginSelectionRepository repository;
    private final Clock clock;

    public LoginSelectionService(LoginSelectionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records a pending choice between accounts the password has already been
     * checked against.
     *
     * @return the raw token; this is the only copy
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(Collection<UUID> accountIds) {
        Instant now = clock.instant();
        repository.deleteExpiredBefore(now);

        String rawToken = SecureTokens.generate();
        repository.save(new LoginSelection(SecureTokens.sha256(rawToken), accountIds, now.plus(TTL)));
        return rawToken;
    }

    /**
     * Exchanges a token for one of its accounts and marks it used.
     *
     * <p>Runs in the caller's transaction on purpose. If the chosen account is
     * then refused (not verified yet, locked), the rollback leaves the token
     * unused, so the person can pick another account without typing their
     * password again.</p>
     *
     * @throws InvalidTokenException   unknown, used or expired token (401)
     * @throws InvalidRequestException the account was not one of the choices (400)
     */
    @Transactional
    public UUID redeem(String rawToken, UUID accountId) {
        Instant now = clock.instant();
        LoginSelection selection = repository.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(pending -> pending.isRedeemable(now))
                .orElseThrow(() -> new InvalidTokenException("Sign-in choice is invalid, expired or already used"));

        if (!selection.offers(accountId)) {
            throw new InvalidRequestException("That account is not one of the choices for this sign-in.");
        }
        selection.markUsed(now);
        return accountId;
    }

    /** How long a freshly issued token stays valid, for the client's countdown. */
    public long ttlSeconds() {
        return TTL.toSeconds();
    }
}
