package com.talentpipe.job.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns the raw {@code page} and {@code size} query parameters into a
 * {@link Pageable} that is always safe to run.
 *
 * <p>Out-of-range values are clamped rather than rejected: a negative page or
 * a size of a million is not worth a 400, it is worth the nearest sensible
 * answer. Clamping also bounds how much one request can ask the database for,
 * whatever the client sends.</p>
 */
final class Paging {

    /**
     * Highest page index honoured. Far beyond any real list here; it exists so
     * {@code page * size} can never overflow the {@code int} offset JPA takes.
     */
    static final int MAX_PAGE = 10_000;

    private Paging() {
        // static helper only
    }

    /**
     * @param page    zero-based page index as sent by the client
     * @param size    page size as sent by the client
     * @param maxSize the largest page this endpoint serves
     */
    static Pageable of(int page, int size, int maxSize, Sort sort) {
        int safePage = Math.min(Math.max(page, 0), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), maxSize);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
