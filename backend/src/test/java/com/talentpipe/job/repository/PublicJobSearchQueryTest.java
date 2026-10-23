package com.talentpipe.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
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

    // -------------------------------------------------------- structured filters

    @Test
    void employmentAndWorkplaceTypes_matchAnyOfTheGivenValues_inAStableOrder() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(filtered(new BoardFilters(null,
                Set.of(EmploymentType.PART_TIME, EmploymentType.CONTRACT),
                Set.of(WorkplaceType.REMOTE), null, null, null)));

        assertThat(query.selectSql())
                .contains(" AND v.employment_type IN (:employmentTypes)")
                .contains(" AND v.workplace_type IN (:workplaceTypes)");
        assertThat(query.parameters())
                .containsEntry("employmentTypes", List.of("CONTRACT", "PART_TIME"))
                .containsEntry("workplaceTypes", List.of("REMOTE"));
    }

    @Test
    void experience_treatsAVacancyThatNamesNoneAsAskingForNone() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(filtered(
                new BoardFilters(null, Set.of(), Set.of(), 1, 5, null)));

        assertThat(query.selectSql())
                .contains(" AND coalesce(v.minimum_experience_years, 0) >= :minExperience")
                .contains(" AND coalesce(v.minimum_experience_years, 0) <= :maxExperience");
        assertThat(query.parameters()).containsEntry("minExperience", 1).containsEntry("maxExperience", 5);
    }

    @Test
    void salary_matchesStatedPayThatOverlapsTheRange_inOnePeriodAndCurrency() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(filtered(new BoardFilters(null, Set.of(), Set.of(),
                null, null, new BoardFilters.Salary(true, PayPeriod.MONTHLY, "LKR",
                        new BigDecimal("100000"), new BigDecimal("200000")))));

        assertThat(query.countSql())
                .contains(" AND (v.salary_min IS NOT NULL OR v.salary_max IS NOT NULL)")
                .contains(" AND v.pay_period = :payPeriod")
                .contains(" AND upper(split_part(v.currency, ' ', 1)) = :currency")
                .contains(" AND coalesce(v.salary_max, v.salary_min) >= :salaryMin")
                .contains(" AND coalesce(v.salary_min, v.salary_max) <= :salaryMax");
        assertThat(query.parameters())
                .containsEntry("payPeriod", "MONTHLY")
                .containsEntry("currency", "LKR")
                .containsEntry("salaryMin", new BigDecimal("100000"))
                .containsEntry("salaryMax", new BigDecimal("200000"));
    }

    @Test
    void industry_isMatchedExactly_onTheCanonicalLabel() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(filtered(
                new BoardFilters("Healthcare", Set.of(), Set.of(), null, null, null)));

        assertThat(query.selectSql()).contains(" AND v.industry = :industry");
        assertThat(query.parameters()).containsOnlyKeys("industry").containsEntry("industry", "Healthcare");
    }

    @Test
    void noStructuredFilters_addNothing() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(filtered(BoardFilters.NONE));

        assertThat(query.countSql()).isEqualTo("SELECT count(*) FROM job_vacancies v WHERE v.status = 'PUBLISHED'");
    }

    @Test
    void newestFirst_overridesRelevance_evenWithAKeyword() {
        PublicJobSearchQuery query = PublicJobSearchQuery.of(
                PublicJobSearchCriteria.of("java", null, null, BoardFilters.NONE, BoardSort.NEWEST));

        assertThat(query.selectSql())
                .contains("v.search_vector @@ websearch_to_tsquery('english', :keyword)")
                .endsWith(" ORDER BY v.published_at DESC, v.id");
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

    private static PublicJobSearchCriteria filtered(BoardFilters filters) {
        return PublicJobSearchCriteria.of(null, null, null, filters, BoardSort.RELEVANT);
    }
}
