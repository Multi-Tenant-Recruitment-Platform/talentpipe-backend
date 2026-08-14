package com.talentpipe.tenant.service;

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
 * <p>Rules (mirrors the frontend normalization in {@code companyProfile.ts}
 * but the server is the source of truth):</p>
 * <ul>
 *   <li>Trim leading/trailing whitespace on every string field.</li>
 *   <li>Collapse repeated internal whitespace on single-line fields.</li>
 *   <li>Preserve multiline semantics (newlines) for {@code description},
 *       {@code culture}, {@code mission}, {@code vision}.</li>
 *   <li>Prepend {@code https://} when a URL field has no scheme.</li>
 *   <li>Deduplicate list values case-insensitively, preserving the first
 *       occurrence (and its original casing).</li>
 *   <li>Convert empty / whitespace-only strings to {@code null} so the
 *       database never stores empty strings for optional fields.</li>
 * </ul>
 */
@Component
public class ProfileNormalizer {

    private static final String HTTPS_PREFIX = "https://";

    // ---------------------------------------------------------------- strings

    /**
     * Normalizes a single-line field: trim, collapse internal whitespace,
     * blank → null.
     */
    public String normalizeLine(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("\\s{2,}", " ");
    }

    /**
     * Normalizes a multiline field: trim only, preserve internal newlines,
     * blank → null.
     */
    public String normalizeMultiline(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Normalizes a URL field: trim, prepend {@code https://} when no scheme
     * is present, blank → null.
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
     * Normalizes a list field: trim each element, remove blank elements,
     * deduplicate case-insensitively (first occurrence wins), return an
     * unmodifiable list. A null input returns an empty list.
     */
    public List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
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
}
