package com.talentpipe.team.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamVacancyResponse(
        UUID id,
        UUID teamId,
        UUID vacancyId,
        Instant createdAt
) {
}
