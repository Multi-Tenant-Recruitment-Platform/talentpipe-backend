package com.talentpipe.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One person on the team screen ({@code GET /api/v1/team}): an active member
 * or a pending invitation.
 *
 * <p>A superset of the fields the listing returned before, so clients written
 * against it keep working.</p>
 *
 * @param role                the old fixed role name, or {@code CUSTOM} for a
 *                            member of a role the company created — use
 *                            {@code roleId} and {@code roleName} instead
 * @param invitationSentAt    when the current invitation email went out;
 *                            {@code null} for active members
 * @param invitationExpiresAt when that invitation stops working;
 *                            {@code null} for active members
 */
public record TeamMemberResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String role,
        UUID roleId,
        String roleName,
        String email,
        String firstName,
        String lastName,
        String status,
        Instant createdAt,
        Instant invitationSentAt,
        Instant invitationExpiresAt
) {
}
