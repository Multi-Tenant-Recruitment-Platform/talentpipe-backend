package com.talentpipe.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A vacancy as the applicants area lists it: what it is, and how many
 * applicants it has in each group. No advert content and no company details.
 *
 * @param employmentType  FULL_TIME, PART_TIME, …; null when not set
 * @param lastAppliedAt   when its newest application arrived; null when nobody has applied
 */
public record ApplicantVacancy(
        UUID id,
        String title,
        String department,
        String location,
        String employmentType,
        String status,
        Instant publishedAt,
        LocalDate applicationDeadline,
        boolean blindMode,
        ApplicantCounts counts,
        Instant lastAppliedAt) {
}
