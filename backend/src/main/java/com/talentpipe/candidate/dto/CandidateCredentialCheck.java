package com.talentpipe.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What checking a password against the candidate account for an email found,
 * without acting on it: no failure counted, no success recorded, no lock
 * cleared. The auth module uses it when the same credentials might also fit
 * a company account and it has to see every match before deciding anything.
 *
 * @param candidateId the candidate's id; {@code null} for {@link Outcome#NO_ACCOUNT}
 * @param lockedUntil when the lock ends; set only for {@link Outcome#LOCKED}
 */
public record CandidateCredentialCheck(Outcome outcome, UUID candidateId, Instant lockedUntil) {

    public enum Outcome {
        /** No candidate uses this email, or the account is disabled. */
        NO_ACCOUNT,
        /** Locked after failed attempts; the password was not checked. */
        LOCKED,
        WRONG_PASSWORD,
        MATCH
    }

    public static final CandidateCredentialCheck NONE = new CandidateCredentialCheck(Outcome.NO_ACCOUNT, null, null);

    public static CandidateCredentialCheck locked(UUID candidateId, Instant lockedUntil) {
        return new CandidateCredentialCheck(Outcome.LOCKED, candidateId, lockedUntil);
    }

    public static CandidateCredentialCheck checked(UUID candidateId, boolean passwordMatches) {
        return new CandidateCredentialCheck(passwordMatches ? Outcome.MATCH : Outcome.WRONG_PASSWORD,
                candidateId, null);
    }

    public boolean matched() {
        return outcome == Outcome.MATCH;
    }
}
