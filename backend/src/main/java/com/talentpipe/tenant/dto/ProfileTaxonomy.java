package com.talentpipe.tenant.dto;

import com.talentpipe.common.util.OptionKey;
import java.util.List;
import java.util.Optional;

/**
 * Canonical option sets for the profile fields that take fixed options:
 * the benefits checkboxes and the industry pickers.
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

    /**
     * The industries a company can work in, as the sign-up and profile forms
     * list them ({@code INDUSTRIES} in the frontend). Stored as these labels,
     * because that is what the forms have always sent.
     *
     * <p>A company has one main industry and up to
     * {@link #MAX_ADDITIONAL_INDUSTRIES} more; each of its vacancies is filed
     * under one of them.</p>
     */
    public static final List<String> INDUSTRIES = List.of(
            "Information Technology",
            "Finance & Banking",
            "Healthcare",
            "Education",
            "Retail & E-commerce",
            "Manufacturing",
            "Hospitality & Leisure",
            "Construction & Engineering",
            "Logistics & Transport",
            "Other");

    /** How many industries a company may list besides its main one. */
    public static final int MAX_ADDITIONAL_INDUSTRIES = 3;

    /**
     * The canonical spelling of an industry, matched by {@link OptionKey}, so
     * {@code "finance and banking"} finds {@code "Finance & Banking"}.
     *
     * @return empty when {@code value} is blank or names no listed industry
     */
    public static Optional<String> industry(String value) {
        String key = OptionKey.of(value);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return INDUSTRIES.stream().filter(option -> OptionKey.of(option).equals(key)).findFirst();
    }

    private ProfileTaxonomy() {
        // constants only
    }
}
