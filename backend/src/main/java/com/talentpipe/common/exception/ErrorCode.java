package com.talentpipe.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable reason carried as {@code code} on every error
 * response.
 *
 * <p>{@code message} is written for people and may be reworded at any time;
 * {@code code} is the contract. A client that needs to react to a particular
 * failure — offer a verification resend, show a workspace picker, re-fetch
 * after a lost update — branches on the code, never on the wording. Before
 * codes existed the SPA detected an unverified account by searching the
 * message for "not verified", which silently broke for the invited-but-not-
 * accepted case whose message reads differently.</p>
 *
 * <p>Codes are only ever added, never renamed or repurposed.</p>
 */
public enum ErrorCode {

    // ---------------------------------------------------------------- 400
    /** One or more request fields failed validation; see {@code fieldErrors}. */
    VALIDATION_FAILED,
    /** The request was well-formed but invalid once normalized. */
    INVALID_REQUEST,
    /** The body could not be parsed (bad JSON, unknown enum value, wrong type). */
    MALFORMED_REQUEST,
    /** A path variable or query parameter has the wrong type. */
    INVALID_PARAMETER,
    /** A required header is missing. */
    MISSING_HEADER,

    // ---------------------------------------------------------------- 401
    /** Wrong email or password — deliberately says nothing about which. */
    INVALID_CREDENTIALS,
    /** No access token was presented. */
    AUTHENTICATION_REQUIRED,
    /** The token (access, refresh or emailed link) is invalid, expired or used. */
    INVALID_TOKEN,

    // ---------------------------------------------------------------- 403
    /** Authenticated, but the caller's role lacks the required permission. */
    ACCESS_DENIED,
    /** Correct credentials, but the email address is not verified yet. */
    ACCOUNT_NOT_VERIFIED,
    /** Correct credentials, but the invitation has not been accepted yet. */
    INVITATION_NOT_ACCEPTED,
    /** Too many failed sign-ins; the account is temporarily locked. */
    ACCOUNT_LOCKED,

    // ---------------------------------------------------------------- 404 / 405
    /** Missing, or owned by another tenant — indistinguishable on purpose. */
    NOT_FOUND,
    METHOD_NOT_ALLOWED,

    // ---------------------------------------------------------------- 409
    /** The request collides with an existing resource (duplicate name, email, …). */
    CONFLICT,
    /** The update was based on a version that is no longer current. */
    CONCURRENT_UPDATE,
    /**
     * The credentials are valid for more than one account; the client must
     * ask which one to sign in to. {@code details} carries the choices.
     */
    WORKSPACE_SELECTION_REQUIRED,

    // ---------------------------------------------------------------- 413 / 415 / 422 / 429 / 500
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    /** A syntactically valid request refused by a business rule. */
    BUSINESS_RULE,
    RATE_LIMITED,
    INTERNAL_ERROR;

    /** The code used when a handler has nothing more specific to say. */
    public static ErrorCode defaultFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> INVALID_REQUEST;
            case UNAUTHORIZED -> AUTHENTICATION_REQUIRED;
            case FORBIDDEN -> ACCESS_DENIED;
            case NOT_FOUND -> NOT_FOUND;
            case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
            case CONFLICT -> CONFLICT;
            case PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE;
            case UNPROCESSABLE_ENTITY -> BUSINESS_RULE;
            case TOO_MANY_REQUESTS -> RATE_LIMITED;
            default -> INTERNAL_ERROR;
        };
    }
}
