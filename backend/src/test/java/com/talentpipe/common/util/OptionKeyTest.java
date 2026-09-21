package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.tenant.dto.ProfileTaxonomy;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link OptionKey}.
 *
 * <p>The key decides which submitted strings count as the same fixed option,
 * so these pin both halves of that contract: which differences it is meant to
 * erase, and which it must preserve.</p>
 */
class OptionKeyTest {

    // ---------------------------------------------------------------- folded away

    @ParameterizedTest
    @ValueSource(strings = {
            "REMOTE_HYBRID", "remote_hybrid", "Remote_Hybrid",
            "remote-hybrid", "remote hybrid", "remotehybrid",
            "  REMOTE_HYBRID  ", "remote.hybrid", "remote/hybrid"})
    void spellingsOfOneOption_shareAKey(String spelling) {
        assertThat(OptionKey.of(spelling)).isEqualTo("remotehybrid");
    }

    @ParameterizedTest
    @CsvSource({
            "'Training & development', traininganddevelopment",
            "'Training and development', traininganddevelopment",
            "'training&development', traininganddevelopment",
            "'R&D', randd"})
    void ampersandAndTheWordAnd_areTheSame(String input, String expected) {
        assertThat(OptionKey.of(input)).isEqualTo(expected);
    }

    @Test
    void digitsSurvive() {
        assertThat(OptionKey.of("Tier-2 support")).isEqualTo("tier2support");
    }

    // ---------------------------------------------------------------- preserved

    @Test
    void differentOptions_keepDifferentKeys() {
        assertThat(OptionKey.of("PAID_LEAVE")).isNotEqualTo(OptionKey.of("PARENTAL_LEAVE"));
        assertThat(OptionKey.of("Intern")).isNotEqualTo(OptionKey.of("Internship"));
    }

    // ---------------------------------------------------------------- degenerate input

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "///", "---", "!@#"})
    void inputWithNoLettersOrDigits_yieldsAnEmptyKey(String input) {
        // an empty key must never be in a whitelist, or these would all match
        assertThat(OptionKey.of(input)).isEmpty();
    }

    /** The one punctuation character that is not dropped but spelled out. */
    @Test
    void bareAmpersand_becomesTheWordAnd() {
        assertThat(OptionKey.of("&")).isEqualTo("and");
    }

    @Test
    void noCanonicalOptionFoldsToAnEmptyKey() {
        // BENEFITS is the sole remaining checkbox-driven taxonomy list.
        List<String> everyOption = ProfileTaxonomy.BENEFITS;

        assertThat(everyOption).isNotEmpty();
        assertThat(everyOption).extracting(OptionKey::of).doesNotContain("");
    }

    // ---------------------------------------------------------------- the reported bug

    /**
     * The regression this was written for: the frontend posts catalogue ids
     * such as {@code REMOTE_HYBRID}, which the profile whitelist has to admit
     * as-is, and the casing the checkbox group happens to send must not change
     * which option is meant.
     *
     * <p>Each expected key is spelled out rather than recomputed from the id,
     * so this checks {@code OptionKey} against an independent answer instead of
     * against a second copy of its own rules.</p>
     */
    @ParameterizedTest
    @CsvSource({
            "REMOTE_HYBRID,      remotehybrid",
            "FLEXIBLE_HOURS,     flexiblehours",
            "HEALTH_INSURANCE,   healthinsurance",
            "TRAINING,           training",
            "PAID_LEAVE,         paidleave",
            "PARENTAL_LEAVE,     parentalleave",
            "PERFORMANCE_BONUS,  performancebonus",
            "STOCK_OPTIONS,      stockoptions",
            "WELLBEING,          wellbeing",
            "TRANSPORT,          transport",
            "MEALS,              meals",
            "RELOCATION,         relocation",
            "CAREER_DEVELOPMENT, careerdevelopment"})
    void benefitCatalogueId_foldsToItsExpectedKey(String id, String expectedKey) {
        assertThat(ProfileTaxonomy.BENEFITS).contains(id);

        assertThat(OptionKey.of(id)).isEqualTo(expectedKey);
        assertThat(OptionKey.of(id.toLowerCase(Locale.ROOT))).isEqualTo(expectedKey);
        assertThat(OptionKey.of(id.replace('_', '-'))).isEqualTo(expectedKey);
    }
}
