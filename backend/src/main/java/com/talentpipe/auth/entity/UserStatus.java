package com.talentpipe.auth.entity;

/** User lifecycle state — mirrors the CHECK constraint on users.status. */
public enum UserStatus {
    ACTIVE,
    INVITED,
    DISABLED
}
