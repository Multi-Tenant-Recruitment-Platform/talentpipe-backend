package com.talentpipe.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A company role as the roles screen shows it.
 *
 * @param systemRole  {@code OWNER}, {@code RECRUITER} or {@code INTERVIEWER}
 *                    for the three default roles, {@code null} for a role the
 *                    company created
 * @param locked      true for Owner, which can be neither edited nor deleted
 * @param deletable   false for the default roles. A custom role that people
 *                    hold can still be deleted, by moving them to another role.
 * @param permissions permission keys, in catalogue order. Owner lists every
 *                    permission, because it always holds all of them.
 * @param memberCount everyone holding the role, pending invitations included
 * @param version     optimistic-lock counter; send it back on {@code PUT}
 */
public record RoleResponse(
        UUID id,
        String name,
        String description,
        String systemRole,
        boolean locked,
        boolean deletable,
        List<String> permissions,
        long memberCount,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
