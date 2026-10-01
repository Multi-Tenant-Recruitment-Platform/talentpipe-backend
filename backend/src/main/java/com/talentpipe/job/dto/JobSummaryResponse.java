package com.talentpipe.job.dto;

import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        UUID tenantId,
        String title,
        String description,
        String status,
        String companyName
) {
}
