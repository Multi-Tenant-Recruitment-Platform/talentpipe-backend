package com.talentpipe.job.dto;

import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One card on the public job board ({@code GET /api/v1/public/jobs}, PB-017):
 * what a candidate needs to decide whether to open the advert.
 *
 * <p>Built for an unauthenticated, cross-tenant audience, so it carries only
 * what the company chose to advertise. Nothing internal is here: no tenant id,
 * no status or version, no recruiter or hiring-manager ids, no screening
 * questions.</p>
 *
 * @param slug                  what identifies the vacancy in a URL — see
 *                              {@code JobSlug}. Keeps resolving for the life
 *                              of the vacancy even if its title is edited.
 * @param companyName           the hiring company's display name
 * @param companyLogoUrl        the company's logo, or {@code null} if it has none
 * @param publishedAt           when the vacancy went live
 * @param acceptingApplications false once the application deadline has passed,
 *                              so the portal can show the advert without
 *                              offering an Apply button that would be refused
 */
public record JobSummaryResponse(
        UUID id,
        String slug,
        String title,
        String companyName,
        String companyLogoUrl,
        String department,
        int openings,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        String location,
        LocalDate applicationDeadline,
        String jobSummary,
        List<String> requiredSkills,
        Integer minimumExperienceYears,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        PayPeriod payPeriod,
        Instant publishedAt,
        boolean acceptingApplications
) {
}
