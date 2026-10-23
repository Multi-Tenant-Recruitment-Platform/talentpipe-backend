package com.talentpipe.job.dto;

import com.talentpipe.job.entity.PayPeriod;
import java.math.BigDecimal;
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
 * @param categories      departments with published vacancies, most populated first
 * @param locations       locations with published vacancies, most populated first
 * @param employmentTypes employment types on offer, e.g. {@code FULL_TIME}, most populated first
 * @param workplaceTypes  workplace types on offer, e.g. {@code REMOTE}, most populated first
 * @param salaryRanges    the pay stated by published vacancies, one entry per
 *                        pay period and currency — what a salary slider spans
 */
public record JobFilterOptionsResponse(
        List<JobFilterOption> categories,
        List<JobFilterOption> locations,
        List<JobFilterOption> employmentTypes,
        List<JobFilterOption> workplaceTypes,
        List<SalaryRange> salaryRanges
) {

    /**
     * The span of pay that published vacancies state for one pay period and
     * currency. A slider is only meaningful within one of these: hourly,
     * monthly and annual pay do not compare, nor do two currencies.
     *
     * @param currency the three-letter code, e.g. {@code LKR}
     * @param min      the lowest amount any of them states
     * @param max      the highest amount any of them states
     * @param count    how many vacancies state pay this way
     */
    public record SalaryRange(PayPeriod payPeriod, String currency, BigDecimal min, BigDecimal max, long count) {
    }
}
