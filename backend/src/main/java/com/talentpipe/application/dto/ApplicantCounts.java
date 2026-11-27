package com.talentpipe.application.dto;

/**
 * How many of a vacancy's applicants are in each group, whatever the list is
 * filtered by — the figures behind the statistics cards and the filter pills.
 *
 * @param all                 every application, withdrawn ones included
 * @param autoAdvanced        advanced: by the screening, or by a recruiter
 * @param advancedByRecruiter of {@code autoAdvanced}, those a recruiter moved on
 * @param manualReview        waiting for a person
 * @param pending             not screened yet
 * @param rejected            by the screening or by a recruiter
 * @param withdrawn           by the candidate
 */
public record ApplicantCounts(long all, long autoAdvanced, long advancedByRecruiter, long manualReview,
                              long pending, long rejected, long withdrawn) {
}
