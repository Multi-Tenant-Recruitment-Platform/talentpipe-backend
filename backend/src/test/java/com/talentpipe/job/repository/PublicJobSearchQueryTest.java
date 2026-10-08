package com.talentpipe.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PublicJobSearchQuery}: which predicates each
 * combination of filters produces, and that user input only ever travels as a
 * bound parameter. The statements are run against a real PostgreSQL — and
 * their plans checked — in {@code PublicJobSearchIntegrationTest}.
 */
class PublicJobSearchQueryTest {

    private static final String SQL_INJECTION = "'; DROP TABLE job_vacancies; --";

    // -------------------------------------------------------------- predicates

    @Test
    void everyStatement_isRestrictedToPublishedVacancies_asALiteral() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of("java", "Eng", "Colombo"));

        // A literal, not a parameter: it is what lets the planner use the partial indexes.
        assertThat(query.selectSql()).contains("WHERE v.status = 'PUBLISHED'");
        assertThat(query.countSql()).contains("WHERE v.status = 'PUBLISHED'");
    }

    @Test
    void noFilters_browsesNewestFirst_withNoPredicatesOrParameters() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.none());

        assertThat(query.selectSql())
                .isEqualTo("SELECT v.* FROM job_vacancies v WHERE v.status = 'PUBLISHED'"
                        + " ORDER BY v.published_at DESC, v.id");
        assertThat(query.countSql())
                .isEqualTo("SELECT count(*) FROM job_vacancies v WHERE v.status = 'PUBLISHED'");
        assertThat(query.parameters()).isEmpty();
    }

    @Test
    void keyword_addsAFullTextMatch_andRanksByRelevance() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of("java", null, null));

        assertThat(query.selectSql())
                .contains("v.search_vector @@ websearch_to_tsquery('english', :keyword)")
                .contains("ORDER BY ts_rank(v.search_vector, websearch_to_tsquery('english', :keyword)) DESC,"
                        + " v.published_at DESC, v.id");
        assertThat(query.parameters()).containsOnlyKeys("keyword").containsEntry("keyword", "java");
    }

    @Test
    void category_addsACaseInsensitiveEquality_matchingTheExpressionIndex() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of(null, "Engineering", null));

        assertThat(query.selectSql())
                .contains("lower(v.department) = lower(:category)")
                .endsWith("ORDER BY v.published_at DESC, v.id");
        assertThat(query.parameters()).containsOnlyKeys("category").containsEntry("category", "Engineering");
    }

    @Test
    void location_addsACaseInsensitiveContainsMatch() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of(null, null, "Colombo"));

        assertThat(query.selectSql()).contains("lower(v.location) LIKE lower(:location) ESCAPE '!'");
        assertThat(query.parameters()).containsOnlyKeys("location").containsEntry("location", "%Colombo%");
    }

    @Test
    void allFilters_combineWithAnd() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of("java", "Eng", "Colombo"));

        assertThat(query.countSql()).isEqualTo(
                "SELECT count(*) FROM job_vacancies v WHERE v.status = 'PUBLISHED'"
                        + " AND v.search_vector @@ websearch_to_tsquery('english', :keyword)"
                        + " AND lower(v.department) = lower(:category)"
                        + " AND lower(v.location) LIKE lower(:location) ESCAPE '!'");
        assertThat(query.parameters()).containsOnlyKeys("keyword", "category", "location");
    }

    @Test
    void countStatement_neverCarriesAnOrderBy() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(PublicJobSearchCriteria.of("java", null, null));

        assertThat(query.countSql()).doesNotContain("ORDER BY");
    }

    // --------------------------------------------------------------- injection

    @Test
    void userInput_isNeverPartOfTheSqlText_onlyOfTheParameters() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(
                PublicJobSearchCriteria.of(SQL_INJECTION, SQL_INJECTION, SQL_INJECTION));

        assertThat(query.selectSql()).doesNotContain("DROP TABLE");
        assertThat(query.countSql()).doesNotContain("DROP TABLE");
        assertThat(query.parameters())
                .containsEntry("keyword", SQL_INJECTION)
                .containsEntry("category", SQL_INJECTION);
    }

    // ----------------------------------------------------------- LIKE patterns

    @Test
    void containsPattern_wrapsPlainTextInWildcards() {
        assertThat(PublicJobSearchQuery.containsPattern("Colombo")).isEqualTo("%Colombo%");
    }

    @Test
    void containsPattern_escapesLikeWildcards_soTheyMatchLiterally() {
        assertThat(PublicJobSearchQuery.containsPattern("100%")).isEqualTo("%100!%%");
        assertThat(PublicJobSearchQuery.containsPattern("a_b")).isEqualTo("%a!_b%");
    }

    @Test
    void containsPattern_escapesTheEscapeCharacterItself() {
        assertThat(PublicJobSearchQuery.containsPattern("Wow!")).isEqualTo("%Wow!!%");
    }
}
