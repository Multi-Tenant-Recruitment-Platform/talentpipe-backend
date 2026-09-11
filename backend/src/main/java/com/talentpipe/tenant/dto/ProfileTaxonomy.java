package com.talentpipe.tenant.dto;

import java.util.List;

/**
 * Canonical option sets for the checkbox-driven profile list fields.
 *
 * <p>These are the values the frontend submits for its fixed checkboxes and
 * the exact spelling and casing the database should hold. They serve two
 * purposes:</p>
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

    /**
     * Benefits are stored as stable identifiers, not as the prose a candidate
     * reads, so that the wording of a perk can be revised without migrating
     * every tenant row and so a candidate-facing search can filter on them.
     * These ids are the {@code BENEFIT_CATALOGUE} ids in the frontend's
     * {@code companyProfile.ts}, which is what the checkbox group binds to and
     * what {@code PATCH /api/v1/tenant} therefore receives; the frontend maps
     * each back to its label for display.
     *
     * <p>The other option sets below are labels because their vocabularies are
     * already short, stable words that read the same to a user and a
     * database.</p>
     */
    public static final List<String> BENEFITS = List.of(
            "REMOTE_HYBRID", "FLEXIBLE_HOURS",
            "HEALTH_INSURANCE", "TRAINING",
            "PAID_LEAVE", "PARENTAL_LEAVE",
            "PERFORMANCE_BONUS", "STOCK_OPTIONS",
            "WELLBEING", "TRANSPORT",
            "MEALS", "RELOCATION",
            "CAREER_DEVELOPMENT");

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
