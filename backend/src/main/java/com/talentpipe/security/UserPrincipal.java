package com.talentpipe.security;

import java.util.UUID;

/**
 * The authenticated identity placed into the Spring {@code SecurityContext}
 * after successful JWT validation. Carries only verified token claims — never
 * a full entity, and never anything secret.
 *
 * @param id       user id (JWT {@code sub})
 * @param tenantId owning tenant, or {@code null} for SUPER_ADMIN platform operators
 * @param role     role name as stored in the {@code role} claim (e.g. COMPANY_ADMIN)
 * @param email    user email from the {@code email} claim
 */
public record UserPrincipal(
        UUID id,
        UUID tenantId,
        String role,
        String email
) {
}
