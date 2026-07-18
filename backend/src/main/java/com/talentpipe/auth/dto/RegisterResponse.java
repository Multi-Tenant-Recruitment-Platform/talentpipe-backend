package com.talentpipe.auth.dto;

import com.talentpipe.tenant.dto.TenantResponse;

/**
 * Result of company onboarding: the created tenant and its first admin.
 * Contains no credential material and no tokens — the flow continues at the
 * login endpoint (email verification will gate this in Week 2).
 */
public record RegisterResponse(
        TenantResponse tenant,
        UserResponse admin
) {
}
