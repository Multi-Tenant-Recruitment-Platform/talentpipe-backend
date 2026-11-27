package com.talentpipe.application.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScreeningBandsTest {

    private final ScreeningBands bands = new ScreeningBands(80, 70, true);

    @ParameterizedTest
    @CsvSource({
            "100, AUTO_ADVANCED",
            "80, AUTO_ADVANCED",   // exactly the auto threshold advances
            "79, MANUAL_REVIEW",
            "70, MANUAL_REVIEW",   // exactly the manual threshold waits for a person
            "69, AUTO_REJECTED",
            "0, AUTO_REJECTED"
    })
    void aScore_fallsInTheBandOfTheThresholdsItReaches(int score, ScreeningOutcome expected) {
        assertThat(bands.outcomeOf(score)).isEqualTo(expected);
    }

    @Test
    void eachOutcome_givesTheReviewStatusTheDashboardGroupsBy() {
        assertThat(ScreeningOutcome.AUTO_ADVANCED.reviewStatus()).isEqualTo(ReviewStatus.AUTO_ADVANCED);
        assertThat(ScreeningOutcome.MANUAL_REVIEW.reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
        assertThat(ScreeningOutcome.AUTO_REJECTED.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void thresholdsThatCannotBeTold_apart_areRefused() {
        assertThatThrownBy(() -> new ScreeningBands(70, 70, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScreeningBands(60, 70, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScreeningBands(101, 70, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decidedReviewStatuses_areTheOnesScreeningLeavesAlone() {
        assertThat(ReviewStatus.ADVANCED_BY_RECRUITER.isDecided()).isTrue();
        assertThat(ReviewStatus.REJECTED.isDecided()).isTrue();
        assertThat(ReviewStatus.WITHDRAWN.isDecided()).isTrue();
        assertThat(ReviewStatus.PENDING_SCREENING.isDecided()).isFalse();
        assertThat(ReviewStatus.MANUAL_REVIEW.isDecided()).isFalse();
    }
}
