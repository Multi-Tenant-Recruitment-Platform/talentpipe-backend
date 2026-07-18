package com.talentpipe.tenant.entity;

/** Tenant lifecycle state — mirrors the CHECK constraint on tenants.status. */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    CANCELLED
}
