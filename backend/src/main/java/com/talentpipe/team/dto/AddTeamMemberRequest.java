package com.talentpipe.team.dto;

import com.talentpipe.team.entity.TeamMemberRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        TeamMemberRole memberRole
) {
}
