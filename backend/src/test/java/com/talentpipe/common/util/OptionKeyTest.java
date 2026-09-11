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
        List<List<String>> sets = List.of(
                ProfileTaxonomy.WORK_MODES, ProfileTaxonomy.BENEFITS,
                ProfileTaxonomy.EMPLOYMENT_TYPES, ProfileTaxonomy.JOB_LEVELS);

        assertThat(sets).allSatisfy(options ->
                assertThat(options).allSatisfy(option ->
                        assertThat(OptionKey.of(option)).isNotEmpty()));
    }

    // ---------------------------------------------------------------- the reported bug

    /**
     * The regression this was written for: the frontend posts catalogue ids
     * such as {@code REMOTE_HYBRID}, which the profile whitelist has to admit
     * as-is, and the casing the checkbox group happens to send must not change
     * which option is meant.
     */
    @Test
    void everyBenefitIdFoldsOntoItselfWhateverItsCasing() {
        assertThat(ProfileTaxonomy.BENEFITS).allSatisfy(id -> {
            String key = OptionKey.of(id);
            assertThat(key).isEqualTo(OptionKey.of(id.toLowerCase(Locale.ROOT)));
            assertThat(key).isEqualTo(OptionKey.of(id.replace('_', '-')));
            assertThat(key).isEqualTo(id.replace("_", "").toLowerCase(Locale.ROOT));
        });
    }
}
