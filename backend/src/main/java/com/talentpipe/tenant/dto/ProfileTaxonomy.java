package com.talentpipe.tenant.dto;

import java.util.List;

/**
 * Canonical option sets for the checkbox-driven profile list fields.
 *
 * <p>These are the values the frontend renders as fixed checkboxes and the
 * exact spelling and casing the database should hold. They serve two purposes:</p>
 * <ol>
 *   <li>{@link UpdateCompanyProfileRequest} rejects anything outside the set
 *       (via {@code @AllowedValues}) with a 400.</li>
 *   <li>{@code ProfileNormalizer} canonicalizes accepted values back to the
 *       spelling here, so a client that sends {@code "remote"} and one that
 *       sends {@code "Remote"} both persist as {@code "Remote"} and the
 *       frontend's exact-match checkbox binding always ticks.</li>
 * </ol>
 *
 * <p>Bean-validation annotations require compile-time constant arrays, so the
 * DTO repeats these values as literals rather than referencing the lists here.
 * {@code ProfileTaxonomyDriftTest} asserts the two copies stay identical.</p>
 */
public final class ProfileTaxonomy {

    public static final List<String> WORK_MODES = List.of(
            "Remote", "Hybrid", "On-site");

    public static final List<String> BENEFITS = List.of(
            "Remote / hybrid work", "Flexible working hours",
            "Health insurance", "Training & development",
            "Generous paid leave", "Parental leave",
            "Performance bonus", "Stock options",
            "Wellbeing & gym support", "Transport allowance",
            "Meals provided", "Relocation support",
            "Career development");

    public static final List<String> EMPLOYMENT_TYPES = List.of(
            "Full-time", "Part-time", "Contract",
            "Internship", "Temporary", "Freelance");

    public static final List<String> JOB_LEVELS = List.of(
            "Intern", "Junior", "Mid-level", "Senior",
            "Lead", "Manager", "Director");

    private ProfileTaxonomy() {
        // constants only
    }
}
