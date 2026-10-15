package com.talentpipe.job.dto;

import java.util.List;

/**
 * The values the public job board can currently be filtered by
 * ({@code GET /api/v1/public/jobs/filters}).
 *
 * <p>Categories and locations are free text entered by each company, so the
 * portal cannot know them in advance; this is where it learns what to put in
 * its filter controls. Only values with at least one published vacancy appear,
 * so a candidate is never offered a filter that returns nothing.</p>
 *
 * @param categories departments with published vacancies, most populated first
 * @param locations  locations with published vacancies, most populated first
 */
public record JobFilterOptionsResponse(List<JobFilterOption> categories, List<JobFilterOption> locations) {
}
