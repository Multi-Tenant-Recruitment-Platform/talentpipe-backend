package com.talentpipe.application.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationScoreTest {

    private static final Instant SCORED_AT = Instant.parse("2026-11-26T08:00:00Z");

    private final UUID applicationId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private static ScreeningResult result(int overallScore) {
        return new ScreeningResult(overallScore, "A strong backend engineer.", List.of("Java"), List.of("No Kafka"),
                List.of(new CriterionScore("Java", 90, "Seven years of Spring Boot")), "gemini-x", "v3", true);
    }

    @Test
    void aFinishedScreening_holdsItsResult() {
        ApplicationScore score = ApplicationScore.createScored(applicationId, tenantId, result(82), SCORED_AT);

        assertThat(score.getApplicationId()).isEqualTo(applicationId);
        assertThat(score.getTenantId()).isEqualTo(tenantId);
        assertThat(score.getStatus()).isEqualTo(ScreeningStatus.SCORED);
        assertThat(score.getOverallScore()).isEqualTo(82);
        assertThat(score.getSummary()).isEqualTo("A strong backend engineer.");
        assertThat(score.getStrengths()).containsExactly("Java");
        assertThat(score.getGaps()).containsExactly("No Kafka");
        assertThat(score.getCriterionScores())
                .containsExactly(new CriterionScore("Java", 90, "Seven years of Spring Boot"));
        assertThat(score.getModelVersion()).isEqualTo("gemini-x");
        assertThat(score.getPromptVersion()).isEqualTo("v3");
        assertThat(score.isCached()).isTrue();
        assertThat(score.getScoredAt()).isEqualTo(SCORED_AT);
        assertThat(score.getFailureReason()).isNull();
    }

    @Test
    void aFailedScreening_holdsOnlyItsReason() {
        ApplicationScore score = ApplicationScore.createFailed(applicationId, tenantId, "Too little text in the CV");

        assertThat(score.getStatus()).isEqualTo(ScreeningStatus.SCORING_FAILED);
        assertThat(score.getFailureReason()).isEqualTo("Too little text in the CV");
        assertThat(score.getOverallScore()).isNull();
        assertThat(score.getScoredAt()).isNull();
        assertThat(score.getStrengths()).isEmpty();
    }

    @Test
    void screeningAgain_replacesTheFailure() {
        ApplicationScore score = ApplicationScore.createFailed(applicationId, tenantId, "Service unavailable");

        score.recordSuccess(result(64), SCORED_AT);

        assertThat(score.getStatus()).isEqualTo(ScreeningStatus.SCORED);
        assertThat(score.getOverallScore()).isEqualTo(64);
        assertThat(score.getFailureReason()).isNull();
    }

    @Test
    void failingAfterASuccess_doesNotLeaveTheOldScoreLookingCurrent() {
        ApplicationScore score = ApplicationScore.createScored(applicationId, tenantId, result(82), SCORED_AT);

        score.recordFailure("Service unavailable");

        assertThat(score.getStatus()).isEqualTo(ScreeningStatus.SCORING_FAILED);
        assertThat(score.getOverallScore()).isNull();
        assertThat(score.getSummary()).isNull();
        assertThat(score.getStrengths()).isEmpty();
        assertThat(score.getGaps()).isEmpty();
        assertThat(score.getCriterionScores()).isEmpty();
        assertThat(score.getModelVersion()).isNull();
        assertThat(score.getPromptVersion()).isNull();
        assertThat(score.isCached()).isFalse();
        assertThat(score.getScoredAt()).isNull();
    }

    @Test
    void aLongReason_isCutToWhatTheColumnHolds_andNoneIsFine() {
        assertThat(ApplicationScore.createFailed(applicationId, tenantId, "x".repeat(501)).getFailureReason())
                .hasSize(ApplicationScore.MAX_FAILURE_REASON);
        assertThat(ApplicationScore.createFailed(applicationId, tenantId, null).getFailureReason()).isNull();
    }

    @Test
    void theListsCannotBeChangedFromOutside() {
        List<String> strengths = new ArrayList<>(List.of("Java"));
        ApplicationScore score = ApplicationScore.createScored(applicationId, tenantId,
                new ScreeningResult(70, "Fine.", strengths, null, null, "gemini-x", "v3", false), SCORED_AT);

        strengths.add("Added later");
        List<String> read = score.getStrengths();

        assertThat(read).containsExactly("Java");
        assertThatThrownBy(() -> read.add("Nope")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(score.getGaps()).isEmpty();
        assertThat(score.getCriterionScores()).isEmpty();
    }

    @Test
    void aScoreAlwaysBelongsToAnApplicationAndACompany() {
        assertThatThrownBy(() -> ApplicationScore.createFailed(null, tenantId, "x"))
                .isInstanceOf(NullPointerException.class).hasMessage("applicationId");
        assertThatThrownBy(() -> ApplicationScore.createFailed(applicationId, null, "x"))
                .isInstanceOf(NullPointerException.class).hasMessage("tenantId");
    }
}
