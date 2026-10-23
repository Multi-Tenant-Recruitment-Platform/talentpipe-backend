package com.talentpipe.common.exception;

/**
 * Thrown when the credentials are correct for more than one account and the
 * request did not say which one it meant. Maps to HTTP 409 with code
 * {@link ErrorCode#WORKSPACE_SELECTION_REQUIRED}; {@link #getDetails()}
 * becomes the envelope's {@code details} and carries the choices.
 *
 * <p>Listing the accounts leaks nothing: the password has already been
 * checked against every one of them.</p>
 */
public class WorkspaceSelectionRequiredException extends RuntimeException {

    private final transient Object details;

    public WorkspaceSelectionRequiredException(Object details) {
        super("This email and password match more than one account. Choose which one to sign in to.");
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }
}
