package com.talentpipe.tenant.service;

import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.common.util.OptionKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Stateless normalizer for company profile fields, applied after bean
 * validation and before persistence in {@code TenantService.updateProfile}.
 *
 * <p>Normalization pipeline (applied in order):</p>
 * <ol>
 *   <li><strong>Sanitize</strong> — strip all HTML tags via OWASP to prevent
 *       stored XSS regardless of whether the client-side sanitized first.</li>
 *   <li><strong>Trim</strong> — remove leading/trailing whitespace.</li>
 *   <li><strong>Collapse</strong> (single-line fields only) — replace runs of
 *       two or more whitespace characters with a single space.</li>
 *   <li><strong>Null-coerce</strong> — convert empty/blank strings to
 *       {@code null} so the database never stores empty strings for optional
 *       fields.</li>
 * </ol>
 *
 * <p>URL fields additionally have {@code https://} prepended when no URI
 * scheme is present. List fields are deduplicated case-insensitively,
 * preserving the first occurrence and its original casing.</p>
 *
 * <p>These rules mirror the frontend normalization in {@code companyProfile.ts}
 * but the server is the authoritative source of truth.</p>
 */
@Component
public class ProfileNormalizer {

    private static final String HTTPS_PREFIX = "https://";

    private final HtmlSanitizer htmlSanitizer;

    public ProfileNormalizer(HtmlSanitizer htmlSanitizer) {
        this.htmlSanitizer = htmlSanitizer;
    }

    // ---------------------------------------------------------------- strings

    /**
     * Normalizes a single-line field: sanitize HTML, trim, collapse internal
     * whitespace to single space, blank → null.
     */
    public String normalizeLine(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = htmlSanitizer.sanitize(value);
        String trimmed = sanitized.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("\\s{2,}", " ");
    }

    /**
     * Normalizes a multiline field: sanitize HTML, trim only outer whitespace,
     * preserve internal newlines, blank → null.
     */
    public String normalizeMultiline(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = htmlSanitizer.sanitize(value);
        String trimmed = sanitized.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Normalizes a URL field: trim, prepend {@code https://} when no scheme
     * is present, blank → null.
     *
     * <p>Note: URL fields are not passed through the HTML sanitizer — a URL
     * is not free text and stripping "markup" from one would corrupt legal
     * query strings. Safety comes from {@code @ValidUrl} instead, which runs
     * before this method and admits only {@code http}/{@code https} URLs with
     * a real host, so no {@code javascript:} payload can reach persistence.</p>
     */
    public String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.contains("://")) {
            trimmed = HTTPS_PREFIX + trimmed;
        }
        return trimmed;
    }

    /**
     * Normalizes a free-text list field: sanitize HTML from each entry, trim
     * it, drop blanks, deduplicate case-insensitively (first occurrence wins,
     * keeping its original casing), and return an unmodifiable list. A null
     * input returns an empty list.
     */
    public List<String> normalizeList(List<String> values) {
        return normalizeList(values, null);
    }

    /**
     * Normalizes a checkbox-driven list field, additionally canonicalizing
     * every entry to its spelling in {@code canonicalOptions}.
     *
     * <p>{@code @AllowedValues} accepts these fields leniently — differing
     * case, punctuation, spacing, and {@code and} for {@code &} all pass — so
     * without this step a client sending {@code "remote_hybrid"} and one
     * sending {@code "REMOTE_HYBRID"} would persist different strings for the
     * same option and the frontend's exact-match checkbox binding would fail
     * to tick for one of them. Matching here uses the same
     * {@link OptionKey} as the validator, so every value that passed
     * validation is canonicalized. Entries with no canonical match are kept
     * verbatim — validation has already rejected genuinely unknown values by
     * this point.</p>
     *
     * @param values          raw entries from the request
     * @param canonicalOptions canonical spellings, or {@code null} for
     *                         free-text lists that have no fixed option set
     */
    public List<String> normalizeList(List<String> values, List<String> canonicalOptions) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            String sanitized = htmlSanitizer.sanitize(raw);
            String trimmed = canonicalize(sanitized.trim(), canonicalOptions);
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                result.add(trimmed); // preserve original casing of first occurrence
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the canonical spelling of {@code value} when
     * {@code canonicalOptions} contains an {@link OptionKey} match, otherwise
     * {@code value} unchanged.
     */
    private String canonicalize(String value, List<String> canonicalOptions) {
        if (canonicalOptions == null) {
            return value;
        }
        String key = OptionKey.of(value);
        for (String option : canonicalOptions) {
            if (OptionKey.of(option).equals(key)) {
                return option;
            }
        }
        return value;
    }
}
