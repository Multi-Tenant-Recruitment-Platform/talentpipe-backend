package com.talentpipe.common.exception;

/**
 * Thrown when an account with correct credentials attempts to log in before
 * its email address has been verified. Maps to HTTP 403 with an actionable
 * message (unlike the deliberately opaque 401 for bad credentials) — by this
 * point the caller has proven they own the credentials, so revealing the
 * verification state leaks nothing.
 */
public class AccountNotVerifiedException extends RuntimeException {

    public AccountNotVerifiedException(String message) {
        super(message);
    }
}
