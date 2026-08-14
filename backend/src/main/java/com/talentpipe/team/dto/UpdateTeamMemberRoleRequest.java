package com.talentpipe.team.dto;

import com.talentpipe.team.entity.TeamMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateTeamMemberRoleRequest(
        @NotNull(message = "Member role is required")
        TeamMemberRole memberRole
) {
}
