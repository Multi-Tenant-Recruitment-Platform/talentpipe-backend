package com.talentpipe.auth.dto;

import java.util.UUID;

/**
 * A workspace member as other modules show them — on a vacancy's hiring
 * team, say. Carries nothing about credentials or permissions.
 *
 * @param roleName the member's company role, e.g. "Recruiter"
 * @param status   ACTIVE, INVITED, PENDING_VERIFICATION or DISABLED; a team
 *                 screen can mark someone who can no longer sign in
 */
public record MemberSummary(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String roleName,
        String status
) {
}
