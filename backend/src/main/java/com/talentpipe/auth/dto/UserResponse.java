package com.talentpipe.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public representation of a user. NEVER carries the password hash or any
 * other credential material — enforced by construction (no such field).
 *
 * <p>Returned inside every sign-in response ({@code login}, {@code refresh},
 * {@code /me}). For a company member it says what they may do, so the SPA can
 * show only what the API will allow.</p>
 *
 * @param tenantId    owning tenant; {@code null} for candidates and SUPER_ADMIN
 * @param tenantName  resolved company name for display (e.g. dashboard
 *                    greeting); {@code null} for candidates and SUPER_ADMIN
 * @param role        the old fixed role name — {@code COMPANY_ADMIN},
 *                    {@code HR_MANAGER}, {@code INTERVIEWER}, {@code CANDIDATE},
 *                    {@code SUPER_ADMIN} — or {@code CUSTOM} for a member of a
 *                    role the company created. Kept for compatibility; new
 *                    code should use {@code roleId} and {@code permissions}.
 * @param roleId      the company role's id; {@code null} for candidates and SUPER_ADMIN
 * @param roleName    the role's display name, e.g. "Panel Interviewer"
 * @param permissions the permission keys the member's role grants, in
 *                    catalogue order. Present in sign-in responses only, and
 *                    omitted where a user appears inside another response.
 *                    Empty for candidates and platform operators, who have no
 *                    company role.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String role,
        UUID roleId,
        String roleName,
        List<String> permissions,
        String email,
        String firstName,
        String lastName,
        String status,
        Instant createdAt
) {
}
