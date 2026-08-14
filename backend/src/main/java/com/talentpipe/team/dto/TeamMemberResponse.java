package com.talentpipe.team.dto;

import com.talentpipe.team.entity.TeamMemberRole;
import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        UUID teamId,
        UUID userId,
        String userEmail,
        String userName,
        TeamMemberRole memberRole,
        Instant createdAt
) {
}
