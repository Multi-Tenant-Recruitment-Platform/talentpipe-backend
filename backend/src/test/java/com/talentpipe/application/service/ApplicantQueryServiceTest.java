package com.talentpipe.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

/** The query-string parsing of the applicant list; the SQL is covered by the integration test. */
class ApplicantQueryServiceTest {

    @Test
    void search_escapesLikeWildcards_soTheyMatchThemselves() {
        assertThat(ApplicantQueryService.likePattern("  Ann_%\\ ")).isEqualTo("%ann\\_\\%\\\\%");
    }

    @Test
    void aBlankSearch_searchesForNothing() {
        assertThat(ApplicantQueryService.likePattern(null)).isNull();
        assertThat(ApplicantQueryService.likePattern("   ")).isNull();
    }

    @Test
    void anOverlongSearch_isRefused() {
        assertThatThrownBy(() -> ApplicantQueryService.likePattern("x".repeat(101)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void bands_acceptTheShortNamesThePillsUse() {
        assertThat(ApplicantQueryService.Band.parse(null)).isEqualTo(ApplicantQueryService.Band.ALL);
        assertThat(ApplicantQueryService.Band.parse("auto")).isEqualTo(ApplicantQueryService.Band.AUTO_ADVANCED);
        assertThat(ApplicantQueryService.Band.parse("MANUAL")).isEqualTo(ApplicantQueryService.Band.MANUAL_REVIEW);
        assertThatThrownBy(() -> ApplicantQueryService.Band.parse("shortlisted"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void anUnknownSort_isRefused() {
        assertThatThrownBy(() -> ApplicantQueryService.SortBy.parse("salary"))
                .isInstanceOf(InvalidRequestException.class);
    }
}
