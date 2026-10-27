package com.talentpipe.common.exception;

/**
 * Thrown when an account with correct credentials attempts to log in before
 * it is ready to: its email address is not verified yet, or it was invited
 * and the invitation has not been accepted. Maps to HTTP 403 with an
 * actionable message (unlike the deliberately opaque 401 for bad credentials)
 * — by this point the caller has proven they own the credentials, so
 * revealing the account state leaks nothing.
 *
 * <p>The two cases carry different {@link ErrorCode}s, because a client
 * reacts to them differently: an unverified account can ask for a new
 * verification email, an invited one cannot.</p>
 */
public class AccountNotVerifiedException extends RuntimeException {

    private final ErrorCode code;

    /** An account whose email address has not been verified yet. */
    public AccountNotVerifiedException(String message) {
        this(message, ErrorCode.ACCOUNT_NOT_VERIFIED);
    }

    private AccountNotVerifiedException(String message, ErrorCode code) {
        super(message);
        this.code = code;
    }

    /** An invited account whose invitation has not been accepted yet. */
    public static AccountNotVerifiedException invitationPending(String message) {
        return new AccountNotVerifiedException(message, ErrorCode.INVITATION_NOT_ACCEPTED);
    }

    public ErrorCode getCode() {
        return code;
    }
}
