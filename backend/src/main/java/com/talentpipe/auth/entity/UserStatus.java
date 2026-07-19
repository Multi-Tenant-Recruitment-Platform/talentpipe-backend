package com.talentpipe.auth.entity;

/** Lifecycle state of a user account. */
public enum UserStatus {
    /** Account is fully active and can authenticate. */
    ACTIVE,
    /** Invited via email; account not yet activated by the invitee (future sprint). */
    INVITED,
    /** Administratively disabled; cannot authenticate. */
    DISABLED,
    /** Registered but email not yet verified; cannot authenticate until verified. */
    PENDING_VERIFICATION
}
