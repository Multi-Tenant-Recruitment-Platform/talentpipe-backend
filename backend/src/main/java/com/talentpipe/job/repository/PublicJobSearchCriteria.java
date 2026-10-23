package com.talentpipe.job.repository;

/**
 * What a candidate asked the public job board for (PB-017): an optional
 * keyword, category and location, the structured {@link BoardFilters}, and
 * an order. Any combination may be absent; with none, the board simply lists
 * every published vacancy, newest first.
 *
 * <p>Build instances with {@link #of}, which normalizes raw query-string
 * input. The repository relies on that: a component is either {@code null}
 * (not filtered on) or a non-blank, length-bounded string.</p>
 *
 * @param keyword  free text matched against title, skills and description
 * @param category the vacancy's department, matched case-insensitively and whole
 * @param location matched case-insensitively anywhere in the vacancy's location
 * @param filters  employment and workplace type, experience and pay
 * @param sort     {@link BoardSort#RELEVANT} ranks keyword matches; without a
 *                 keyword both orders are newest first
 */
public record PublicJobSearchCriteria(String keyword, String category, String location,
                                      BoardFilters filters, BoardSort sort) {

    public PublicJobSearchCriteria {
        filters = filters == null ? BoardFilters.NONE : filters;
        sort = sort == null ? BoardSort.RELEVANT : sort;
    }

    /**
     * Longest keyword search accepted. A real query is a few words; the cap
     * only bounds the work a pasted wall of text can ask of the parser.
     */
    static final int MAX_KEYWORD_LENGTH = 200;

    /** Width of the {@code department} and {@code location} columns — nothing longer can match. */
    static final int MAX_FILTER_LENGTH = 120;

    /** Criteria that filter on nothing. */
    public static PublicJobSearchCriteria none() {
        return new PublicJobSearchCriteria(null, null, null, BoardFilters.NONE, BoardSort.RELEVANT);
    }

    /**
     * Normalizes raw request parameters: trims, collapses runs of whitespace,
     * treats blank as absent and cuts over-long input to its limit.
     */
    public static PublicJobSearchCriteria of(String keyword, String category, String location) {
        return of(keyword, category, location, BoardFilters.NONE, BoardSort.RELEVANT);
    }

    /** {@link #of(String, String, String)} with the structured filters and an order. */
    public static PublicJobSearchCriteria of(String keyword, String category, String location,
                                             BoardFilters filters, BoardSort sort) {
        return new PublicJobSearchCriteria(
                clean(keyword, MAX_KEYWORD_LENGTH),
                clean(category, MAX_FILTER_LENGTH),
                clean(location, MAX_FILTER_LENGTH),
                filters,
                sort);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public boolean hasCategory() {
        return category != null;
    }

    public boolean hasLocation() {
        return location != null;
    }

    /** Results go best match first: a keyword was given and the order asked for is relevance. */
    public boolean ranksByRelevance() {
        return hasKeyword() && sort == BoardSort.RELEVANT;
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() > maxLength ? collapsed.substring(0, maxLength).trim() : collapsed;
    }
}
