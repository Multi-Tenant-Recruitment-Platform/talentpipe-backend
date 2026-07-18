package com.talentpipe.auth.entity;

/** Fixed role vocabulary — mirrors the CHECK constraint on roles.name (seeded by V3). */
public enum RoleName {
    SUPER_ADMIN,
    COMPANY_ADMIN,
    HR_MANAGER,
    INTERVIEWER,
    CANDIDATE
}
