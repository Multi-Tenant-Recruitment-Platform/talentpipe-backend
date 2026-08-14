package com.talentpipe.team.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeamDetailResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        String department,
        UUID leadId,
        String leadName,
        List<TeamMemberResponse> members,
        List<TeamVacancyResponse> vacancies,
        Instant createdAt,
        Instant updatedAt
) {
}
