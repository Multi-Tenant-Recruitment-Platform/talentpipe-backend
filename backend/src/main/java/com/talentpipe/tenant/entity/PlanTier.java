package com.talentpipe.tenant.entity;

/** Subscription tier — mirrors the CHECK constraint on tenants.plan_tier. */
public enum PlanTier {
    TRIAL,
    STANDARD,
    ENTERPRISE
}
