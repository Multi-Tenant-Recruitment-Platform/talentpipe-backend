package com.talentpipe.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the access audit trail ({@code GET /api/v1/roles/audit}).
 *
 * @param action        {@code ROLE_CREATED}, {@code ROLE_UPDATED},
 *                      {@code ROLE_DELETED} or {@code MEMBER_ROLE_CHANGED}
 * @param roleName      the role's name at the time
 * @param targetUserId  for {@code MEMBER_ROLE_CHANGED}, whose role changed
 * @param previousValue what it was, as readable text
 * @param newValue      what it became
 */
public record RoleAuditEventResponse(
        UUID id,
        String action,
        UUID actorUserId,
        UUID roleId,
        String roleName,
        UUID targetUserId,
        String previousValue,
        String newValue,
        Instant occurredAt
) {
}
