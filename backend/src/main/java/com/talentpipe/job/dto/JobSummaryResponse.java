package com.talentpipe.job.dto;

import java.util.UUID;

/**
 * Public job-board listing item (PB-005, partial).
 *
 * <p>TODO(sprint2): contract placeholder only — fields will grow (location,
 * employment type, posted date, …) when the Job module lands in a later
 * sprint. No rows are produced this week.</p>
 */
public record JobSummaryResponse(
        UUID id,
        String title,
        String companyName
) {
}
