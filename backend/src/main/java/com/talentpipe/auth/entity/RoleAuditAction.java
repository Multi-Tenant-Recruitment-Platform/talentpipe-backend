package com.talentpipe.auth.entity;

/** What a {@link RoleAuditEvent} records — mirrors the CHECK constraint on its table. */
public enum RoleAuditAction {
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    MEMBER_ROLE_CHANGED
}
