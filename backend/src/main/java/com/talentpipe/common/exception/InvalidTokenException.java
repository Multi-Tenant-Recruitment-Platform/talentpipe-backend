package com.talentpipe.common.exception;

/**
 * Maps to 401. Thrown when a presented token (access or refresh) is missing,
 * malformed, expired, tampered with, revoked, or of the wrong type. The
 * message is intentionally generic in responses — token failures must not
 * disclose which precise check failed.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
