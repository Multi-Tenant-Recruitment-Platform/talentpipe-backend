package com.talentpipe.job.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The SQL of one public job-board search (PB-017): the statement text for a
 * given {@link PublicJobSearchCriteria}, and the values to bind to it.
 *
 * <p>Separated from {@link JobVacancySearchRepositoryImpl}, which only
 * executes it, so that the exact statements production runs can be inspected
 * without a database session — the integration suite feeds them to
 * {@code EXPLAIN} to prove each one is served by the index built for it.</p>
 *
 * <h3>Why the SQL is assembled, not fixed</h3>
 * <p>Each filter is optional. The usual single-statement spelling —
 * {@code (:category IS NULL OR lower(department) = :category)} — hides which
 * filters are active from the planner whenever it reuses a plan, and it then
 * has to scan every published row. Here the statement contains only the
 * predicates that apply, so every combination gets a plan that can use the
 * index built for it (the partial indexes in V11):</p>
 * <ul>
 *   <li>no filter — {@code idx_job_vacancies_public_recent}, already in the
 *       ORDER BY's order, so no sort;</li>
 *   <li>keyword — {@code idx_job_vacancies_public_search} (GIN on the
 *       generated {@code search_vector});</li>
 *   <li>category — {@code idx_job_vacancies_public_category};</li>
 *   <li>location — {@code idx_job_vacancies_public_location} (trigram GIN).</li>
 * </ul>
 *
 * <h3>Why that is safe</h3>
 * <p>Only the constants below are ever concatenated. Everything a candidate
 * typed travels as a bound parameter, and the keyword goes through
 * {@code websearch_to_tsquery}, which accepts any text without raising a
 * syntax error — so neither injection nor a stray quote or operator in the
 * search box can break the query.</p>
 *
 * <p>The status is written as the literal {@code 'PUBLISHED'} rather than
 * bound, on purpose: it is what lets the planner prove the partial indexes'
 * predicate for any plan it builds.</p>
 */
public final class PublicJobSearchQuery {

    private static final String FROM_PUBLISHED = " FROM job_vacancies v WHERE v.status = 'PUBLISHED'";

    /** The candidate's keywords as a text-search query; quotes, OR and -exclusions are understood. */
    private static final String KEYWORD_QUERY = "websearch_to_tsquery('english', :keyword)";

    private static final String MATCHES_KEYWORD = " AND v.search_vector @@ " + KEYWORD_QUERY;
    private static final String MATCHES_CATEGORY = " AND lower(v.department) = lower(:category)";
    private static final String MATCHES_LOCATION = " AND lower(v.location) LIKE lower(:location) ESCAPE '!'";

    /** Newest first; id makes the order total so paging is stable. */
    private static final String ORDER_BY_RECENT = " ORDER BY v.published_at DESC, v.id";

    /** Best match first: a hit in the title outranks one in the body (weights set in V11). */
    private static final String ORDER_BY_RELEVANCE =
            " ORDER BY ts_rank(v.search_vector, " + KEYWORD_QUERY + ") DESC, v.published_at DESC, v.id";

    /** Escape character for LIKE; chosen over the default backslash to keep it out of string literals. */
    private static final char LIKE_ESCAPE = '!';

    private final String where;
    private final String orderBy;
    private final Map<String, Object> parameters;

    private PublicJobSearchQuery(String where, String orderBy, Map<String, Object> parameters) {
        this.where = where;
        this.orderBy = orderBy;
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    /** Builds the query for the given criteria, including only the predicates that apply. */
    public static PublicJobSearchQuery of(PublicJobSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(FROM_PUBLISHED);
        Map<String, Object> parameters = new LinkedHashMap<>();

        if (criteria.hasKeyword()) {
            where.append(MATCHES_KEYWORD);
            parameters.put("keyword", criteria.keyword());
        }
        if (criteria.hasCategory()) {
            where.append(MATCHES_CATEGORY);
            parameters.put("category", criteria.category());
        }
        if (criteria.hasLocation()) {
            where.append(MATCHES_LOCATION);
            parameters.put("location", containsPattern(criteria.location()));
        }
        return new PublicJobSearchQuery(
                where.toString(),
                criteria.hasKeyword() ? ORDER_BY_RELEVANCE : ORDER_BY_RECENT,
                parameters);
    }

    /** One page of matching rows, in display order. The caller applies offset and limit. */
    public String selectSql() {
        return "SELECT v.*" + where + orderBy;
    }

    /** How many rows match in total — the page envelope's {@code totalElements}. */
    public String countSql() {
        return "SELECT count(*)" + where;
    }

    /** Named parameter values for both statements; empty when nothing is filtered on. */
    public Map<String, Object> parameters() {
        return parameters;
    }

    /**
     * Wraps {@code text} for a "contains" match, neutralizing the characters
     * LIKE would otherwise treat as wildcards — a candidate searching for
     * {@code "100%"} means the percent sign, not "anything".
     */
    static String containsPattern(String text) {
        StringBuilder pattern = new StringBuilder(text.length() + 2).append('%');
        for (char c : text.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
