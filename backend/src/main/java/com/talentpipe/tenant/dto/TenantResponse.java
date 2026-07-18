package com.talentpipe.tenant.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a tenant. This DTO — never the {@code Tenant}
 * entity — is what crosses the controller boundary and the module boundary.
 */
public record TenantResponse(
        UUID id,
        String name,
        String subdomain,
        String industry,
        String planTier,
        String status,
        Instant createdAt
) {
}
