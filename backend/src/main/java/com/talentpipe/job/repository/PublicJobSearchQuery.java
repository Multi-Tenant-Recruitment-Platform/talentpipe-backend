package com.talentpipe.job.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 * index built for it (the partial indexes in V12):</p>
 * <ul>
 *   <li>no filter — {@code idx_job_vacancies_public_recent}, already in the
 *       ORDER BY's order, so no sort;</li>
 *   <li>keyword — {@code idx_job_vacancies_public_search} (GIN on the
 *       generated {@code search_vector});</li>
 *   <li>category — {@code idx_job_vacancies_public_category};</li>
 *   <li>location — {@code idx_job_vacancies_public_location} (trigram GIN);</li>
 *   <li>industry — {@code idx_job_vacancies_public_industry} (V19).</li>
 * </ul>
 *
 * <p>The structured filters (employment and workplace type, experience, pay)
 * have no index of their own, on purpose. Each one keeps a large share of
 * the board, so an index on it would rarely beat reading the newest adverts
 * off {@code idx_job_vacancies_public_recent} and checking them as they go
 * by, which is what the planner does.</p>
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
    private static final String MATCHES_INDUSTRY = " AND v.industry = :industry";
    private static final String MATCHES_EMPLOYMENT_TYPE = " AND v.employment_type IN (:employmentTypes)";
    private static final String MATCHES_WORKPLACE_TYPE = " AND v.workplace_type IN (:workplaceTypes)";

    /** A vacancy that names no experience asks for none. */
    private static final String YEARS_ASKED = "coalesce(v.minimum_experience_years, 0)";
    private static final String MATCHES_MIN_EXPERIENCE = " AND " + YEARS_ASKED + " >= :minExperience";
    private static final String MATCHES_MAX_EXPERIENCE = " AND " + YEARS_ASKED + " <= :maxExperience";

    private static final String STATES_SALARY = " AND (v.salary_min IS NOT NULL OR v.salary_max IS NOT NULL)";
    private static final String MATCHES_PAY_PERIOD = " AND v.pay_period = :payPeriod";
    /** Currency is stored as the form shows it, "LKR — Sri Lankan rupee"; the code is its first word. */
    private static final String MATCHES_CURRENCY = " AND upper(split_part(v.currency, ' ', 1)) = :currency";
    /**
     * The stated pay overlaps the wanted range. A one-sided salary is a range
     * of one point, and a vacancy stating none never overlaps anything.
     */
    private static final String PAYS_AT_LEAST = " AND coalesce(v.salary_max, v.salary_min) >= :salaryMin";
    private static final String PAYS_AT_MOST = " AND coalesce(v.salary_min, v.salary_max) <= :salaryMax";

    /** Newest first; id makes the order total so paging is stable. */
    private static final String ORDER_BY_RECENT = " ORDER BY v.published_at DESC, v.id";

    /** Best match first: a hit in the title outranks one in the body (weights set in V12). */
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
        appendFilters(criteria.filters(), where, parameters);
        return new PublicJobSearchQuery(
                where.toString(),
                criteria.ranksByRelevance() ? ORDER_BY_RELEVANCE : ORDER_BY_RECENT,
                parameters);
    }

    private static void appendFilters(BoardFilters filters, StringBuilder where, Map<String, Object> parameters) {
        appendIfSet(where, parameters, MATCHES_INDUSTRY, "industry", filters.industry());
        if (!filters.employmentTypes().isEmpty()) {
            where.append(MATCHES_EMPLOYMENT_TYPE);
            parameters.put("employmentTypes", names(filters.employmentTypes()));
        }
        if (!filters.workplaceTypes().isEmpty()) {
            where.append(MATCHES_WORKPLACE_TYPE);
            parameters.put("workplaceTypes", names(filters.workplaceTypes()));
        }
        appendIfSet(where, parameters, MATCHES_MIN_EXPERIENCE, "minExperience", filters.minExperience());
        appendIfSet(where, parameters, MATCHES_MAX_EXPERIENCE, "maxExperience", filters.maxExperience());

        BoardFilters.Salary salary = filters.salary();
        if (salary.statedOnly()) {
            where.append(STATES_SALARY);
        }
        appendIfSet(where, parameters, MATCHES_PAY_PERIOD, "payPeriod",
                salary.payPeriod() == null ? null : salary.payPeriod().name());
        appendIfSet(where, parameters, MATCHES_CURRENCY, "currency", salary.currency());
        appendIfSet(where, parameters, PAYS_AT_LEAST, "salaryMin", salary.min());
        appendIfSet(where, parameters, PAYS_AT_MOST, "salaryMax", salary.max());
    }

    private static void appendIfSet(StringBuilder where, Map<String, Object> parameters,
                                    String predicate, String name, Object value) {
        if (value != null) {
            where.append(predicate);
            parameters.put(name, value);
        }
    }

    /** Enum values as stored, in a stable order so the same filters always produce the same statement. */
    private static List<String> names(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
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
