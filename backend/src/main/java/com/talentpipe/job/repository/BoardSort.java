package com.talentpipe.job.repository;

import java.util.Locale;
import java.util.Optional;

/** How the job board orders its results. */
public enum BoardSort {

    /** Best keyword match first; without a keyword, the same as {@link #NEWEST}. */
    RELEVANT,

    /** Most recently published first. */
    NEWEST;

    /** {@code relevant} / {@code newest}, ignoring case and surrounding spaces. */
    public static Optional<BoardSort> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        for (BoardSort sort : values()) {
            if (sort.name().equals(wanted)) {
                return Optional.of(sort);
            }
        }
        return Optional.empty();
    }
}
