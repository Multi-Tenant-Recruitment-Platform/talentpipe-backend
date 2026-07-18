package com.talentpipe.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a user. NEVER carries the password hash or any
 * other credential material — enforced by construction (no such field).
 *
 * @param tenantId   owning tenant; {@code null} for SUPER_ADMIN
 * @param tenantName resolved company name for display (e.g. dashboard greeting);
 *                   {@code null} for SUPER_ADMIN
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String role,
        String email,
        String firstName,
        String lastName,
        String status,
        Instant createdAt
) {
}
