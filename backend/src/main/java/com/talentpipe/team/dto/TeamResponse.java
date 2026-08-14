package com.talentpipe.team.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        String department,
        UUID leadId,
        String leadName,
        long memberCount,
        long vacancyCount,
        Instant createdAt,
        Instant updatedAt
) {
}
