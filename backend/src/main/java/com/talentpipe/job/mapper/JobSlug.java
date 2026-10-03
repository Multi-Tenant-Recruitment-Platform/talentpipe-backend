package com.talentpipe.job.mapper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The public URL key of a vacancy:
 * {@code senior-backend-engineer-acme-8f0c2c1e4b7a4d2e9c3a1b2c3d4e5f60}.
 *
 * <p>Readable words for people and search engines, followed by the vacancy id
 * as 32 hex digits. Only the id is used to find the vacancy; the words are
 * decoration. That one decision buys three properties a slug stored in its own
 * column would have to work for:</p>
 * <ul>
 *   <li><strong>Unique by construction</strong> — two "Software Engineer"
 *       vacancies at two companies called "Acme" still differ in the id, so
 *       there is no collision to detect and no suffix to invent.</li>
 *   <li><strong>Stable under edits</strong> — a published vacancy's title can
 *       be corrected, and a link shared before the correction still resolves,
 *       because the part that changed is not the part that is looked up.</li>
 *   <li><strong>Nothing to store or migrate</strong> — the slug is derived on
 *       the way out and parsed on the way in.</li>
 * </ul>
 *
 * <p>The word part follows the frontend's {@code jobSlug} (title, then company,
 * accents folded, capped in length) so links look the same whichever side
 * built them.</p>
 */
public final class JobSlug {

    /** Cap on the readable part, so a very long title does not produce an unwieldy URL. */
    private static final int MAX_WORDS_LENGTH = 80;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC_RUN = Pattern.compile("[^a-z0-9]+");
    /** Leading hyphens, or trailing ones — grouped so the anchors' scope is unmistakable. */
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(?:^-+)|(?:-+$)");

    /** A slug's final segment: the id, as exactly 32 hex digits. */
    private static final Pattern TRAILING_COMPACT_ID = Pattern.compile("(?:^|-)([0-9a-f]{32})$");

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private JobSlug() {
        // static helper only
    }

    /** Builds the slug for a vacancy. Never blank: with no usable words it is the bare id. */
    public static String of(String title, String companyName, UUID id) {
        String words = slugify(nullToEmpty(title) + " " + nullToEmpty(companyName));
        String compactId = id.toString().replace("-", "");
        return words.isEmpty() ? compactId : words + "-" + compactId;
    }

    /**
     * Extracts the vacancy id from whatever the public detail URL carried.
     *
     * <p>Accepts a slug built by {@link #of} — with any words in front, since
     * they may predate a title edit — and also a plain UUID, so API clients
     * that only hold the id need not build a slug.</p>
     *
     * @return the id, or empty if {@code slugOrId} contains none (the caller
     *         answers 404)
     */
    public static Optional<UUID> resolveId(String slugOrId) {
        if (slugOrId == null) {
            return Optional.empty();
        }
        String value = slugOrId.trim().toLowerCase(Locale.ROOT);
        if (CANONICAL_UUID.matcher(value).matches()) {
            return Optional.of(UUID.fromString(value));
        }
        Matcher trailingId = TRAILING_COMPACT_ID.matcher(value);
        if (!trailingId.find()) {
            return Optional.empty();
        }
        String hex = trailingId.group(1);
        return Optional.of(UUID.fromString(String.join("-",
                hex.substring(0, 8), hex.substring(8, 12), hex.substring(12, 16),
                hex.substring(16, 20), hex.substring(20))));
    }

    /** "Ingénieur Senior (Java)" → "ingenieur-senior-java", cut at a word boundary if too long. */
    private static String slugify(String text) {
        String folded = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("");
        String hyphenated = NON_ALPHANUMERIC_RUN.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll("-");
        String slug = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
        if (slug.length() <= MAX_WORDS_LENGTH) {
            return slug;
        }
        String cut = slug.substring(0, MAX_WORDS_LENGTH);
        int lastBreak = cut.lastIndexOf('-');
        return lastBreak > 0 ? cut.substring(0, lastBreak) : cut;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
