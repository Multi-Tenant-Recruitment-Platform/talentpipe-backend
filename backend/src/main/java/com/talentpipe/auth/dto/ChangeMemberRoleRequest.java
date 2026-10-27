package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code PUT /api/v1/team/members/{userId}/role}. */
public record ChangeMemberRoleRequest(

        @NotNull(message = "Choose the role to give this member.")
        UUID roleId
) {
}
