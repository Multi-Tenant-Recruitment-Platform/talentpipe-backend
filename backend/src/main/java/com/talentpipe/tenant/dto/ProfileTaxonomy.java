package com.talentpipe.tenant.dto;

import java.util.List;

/**
 * Canonical option sets for the checkbox-driven profile list fields.
 *
 * <p>These are catalogue ids — upper-snake-case identifiers that the frontend
 * submits and the database persists. The validator ({@code @AllowedValues})
 * and normalizer ({@code ProfileNormalizer}) both work via {@link
 * com.talentpipe.common.util.OptionKey}, which folds case, punctuation, and
 * {@code &}/{@code and} away, so a client that sends {@code "remote_hybrid"}
 * or {@code "Remote-Hybrid"} is treated identically to {@code "REMOTE_HYBRID"}
 * and gets it rewritten to the canonical spelling here before persistence.</p>
 *
 * <p>Bean-validation annotations require compile-time constant arrays, so
 * {@link UpdateCompanyProfileRequest} repeats these values as literals rather
 * than referencing the lists here. {@code ProfileTaxonomyDriftTest} asserts
 * the two copies stay identical.</p>
 */
public final class ProfileTaxonomy {

    public static final List<String> BENEFITS = List.of(
            "REMOTE_HYBRID",
            "FLEXIBLE_HOURS",
            "HEALTH_INSURANCE",
            "TRAINING",
            "PAID_LEAVE",
            "PARENTAL_LEAVE",
            "PERFORMANCE_BONUS",
            "STOCK_OPTIONS",
            "WELLBEING",
            "TRANSPORT",
            "MEALS",
            "RELOCATION",
            "CAREER_DEVELOPMENT");

    private ProfileTaxonomy() {
        // constants only
    }
}
