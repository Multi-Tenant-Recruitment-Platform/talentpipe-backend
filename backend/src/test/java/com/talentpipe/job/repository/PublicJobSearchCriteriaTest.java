package com.talentpipe.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the query-string normalization in {@link PublicJobSearchCriteria}. */
class PublicJobSearchCriteriaTest {

    @Test
    void none_filtersOnNothing() {
        PublicJobSearchCriteria criteria = PublicJobSearchCriteria.none();

        assertThat(criteria.hasKeyword()).isFalse();
        assertThat(criteria.hasCategory()).isFalse();
        assertThat(criteria.hasLocation()).isFalse();
    }

    @Test
    void of_trimsAndCollapsesWhitespace() {
        PublicJobSearchCriteria criteria =
                PublicJobSearchCriteria.of("  senior   java ", " Engineering ", "Colombo,\tSri Lanka");

        assertThat(criteria.keyword()).isEqualTo("senior java");
        assertThat(criteria.category()).isEqualTo("Engineering");
        assertThat(criteria.location()).isEqualTo("Colombo, Sri Lanka");
    }

    @Test
    void of_treatsNullAndBlankAsAbsent() {
        PublicJobSearchCriteria criteria = PublicJobSearchCriteria.of(null, "", "   ");

        assertThat(criteria).isEqualTo(PublicJobSearchCriteria.none());
    }

    @Test
    void of_filtersIndependently_anyCombinationMayBePresent() {
        PublicJobSearchCriteria criteria = PublicJobSearchCriteria.of(null, "Engineering", null);

        assertThat(criteria.hasKeyword()).isFalse();
        assertThat(criteria.hasCategory()).isTrue();
        assertThat(criteria.hasLocation()).isFalse();
    }

    @Test
    void of_cutsOverLongInputToItsLimit() {
        PublicJobSearchCriteria criteria =
                PublicJobSearchCriteria.of("k".repeat(5_000), "c".repeat(5_000), "l".repeat(5_000));

        assertThat(criteria.keyword()).hasSize(PublicJobSearchCriteria.MAX_KEYWORD_LENGTH);
        assertThat(criteria.category()).hasSize(PublicJobSearchCriteria.MAX_FILTER_LENGTH);
        assertThat(criteria.location()).hasSize(PublicJobSearchCriteria.MAX_FILTER_LENGTH);
    }

    @Test
    void of_keepsSearchOperatorsAndPunctuation_theQueryParserHandlesThem() {
        PublicJobSearchCriteria criteria = PublicJobSearchCriteria.of("\"spring boot\" OR node.js -php", null, null);

        assertThat(criteria.keyword()).isEqualTo("\"spring boot\" OR node.js -php");
    }
}
