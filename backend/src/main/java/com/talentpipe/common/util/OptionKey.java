package com.talentpipe.common.util;

import java.util.Locale;

/**
 * Reduces a fixed-option string to a comparison key, so that the same option
 * written in different-but-equivalent ways matches.
 *
 * <p>The checkbox-driven profile fields ({@code workModes}, {@code benefits},
 * {@code employmentTypes}, {@code jobLevels}) each have a fixed whitelist —
 * identifiers for {@code benefits}, short labels for the rest. Clients do not
 * all submit the exact canonical string: an id may arrive lower-cased
 * ({@code remote_hybrid}) or hyphenated, a label may arrive with different
 * spacing around punctuation, and {@code "&"} is routinely written out as
 * {@code "and"}. None of those are different options — they are different
 * spellings of one option — but an exact (or merely case-insensitive)
 * comparison rejects them with a 400 the user cannot act on.</p>
 *
 * <p>The key therefore folds away everything that carries no meaning for
 * identity:</p>
 * <ul>
 *   <li>case — {@code "REMOTE"} and {@code "remote"} are one option;</li>
 *   <li>{@code &} vs {@code and} — {@code "Training & development"} and
 *       {@code "Training and development"} are one option;</li>
 *   <li>all non-alphanumeric characters — spaces, hyphens, slashes and
 *       underscores, so {@code "REMOTE_HYBRID"}, {@code "remote-hybrid"} and
 *       {@code "remotehybrid"} are one option.</li>
 * </ul>
 *
 * <p>What survives is the letters and digits, so genuinely different options
 * still differ: {@code "Free coffee"} matches nothing in the benefits
 * whitelist and is still rejected. The canonical option sets are checked for
 * key collisions by {@code ProfileTaxonomyDriftTest}.</p>
 *
 * <p>Used by {@link AllowedValuesValidator} to decide whether a submitted
 * value is permitted, and by {@code ProfileNormalizer} to map it back to the
 * canonical spelling before persistence — the same key on both sides, so
 * anything accepted is also canonicalized.</p>
 */
public final class OptionKey {

    /**
     * Returns the comparison key for {@code value}: lowercased, with
     * {@code &} expanded to {@code and} and every non-alphanumeric character
     * removed. A {@code null} or punctuation-only input yields an empty
     * string.
     *
     * @param value raw option string from a request or a canonical list
     * @return the folded key, never {@code null}
     */
    public static String of(String value) {
        if (value == null) {
            return "";
        }
        String expanded = value.toLowerCase(Locale.ROOT).replace("&", " and ");
        StringBuilder key = new StringBuilder(expanded.length());
        expanded.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(key::appendCodePoint);
        return key.toString();
    }

    private OptionKey() {
        // static helper only
    }
}
