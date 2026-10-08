package com.talentpipe.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId,
        UUID candidateId,
        UUID tenantId,
        String jobTitle,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant withdrawnAt
) {
}
