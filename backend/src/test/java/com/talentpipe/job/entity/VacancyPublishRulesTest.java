package com.talentpipe.job.entity;

import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VacancyPublishRules}: what an advert must contain to
 * be public, and the sentence a recruiter is shown when it does not.
 */
class VacancyPublishRulesTest {

    @Test
    void completeContent_hasNothingMissing() {
        assertThat(VacancyPublishRules.missing(VacancyFixtures.complete().build(), TODAY)).isEmpty();
    }

    @Test
    void emptyDraft_isMissingEveryRequiredItem_inFormReadingOrder() {
        assertThat(VacancyPublishRules.missing(VacancyFixtures.blank().build(), TODAY)).containsExactly(
                "a job title",
                "a department",
                "an employment type",
                "a workplace type",
                "a location",
                "an application deadline",
                "a job summary",
                "a job description",
                "at least one key responsibility",
                "at least one required skill");
    }

    @Test
    void whitespaceOnlyText_countsAsMissing() {
        VacancyContent content = VacancyFixtures.complete().title("   ").location("\t").build();

        assertThat(VacancyPublishRules.missing(content, TODAY)).containsExactly("a job title", "a location");
    }

    @Test
    void deadlineToday_isAcceptable() {
        VacancyContent content = VacancyFixtures.complete().applicationDeadline(TODAY).build();

        assertThat(VacancyPublishRules.missing(content, TODAY)).isEmpty();
    }

    @Test
    void deadlineYesterday_isReportedAsNeedingALaterDate() {
        VacancyContent content = VacancyFixtures.complete().applicationDeadline(TODAY.minusDays(1)).build();

        assertThat(VacancyPublishRules.missing(content, TODAY))
                .containsExactly("an application deadline that is today or later");
    }

    @Test
    void optionalSections_areNeverRequired() {
        // No salary, benefits, schedule or assignees: still publishable.
        VacancyContent content = VacancyFixtures.complete()
                .salary(null, null).benefits(List.of()).workingDays(List.of())
                .preferredSkills(List.of()).recruitmentPipelineId(null)
                .build();

        assertThat(VacancyPublishRules.missing(content, TODAY)).isEmpty();
    }

    // --------------------------------------------------------------- sentences

    @Test
    void requireComplete_passesSilentlyWhenNothingIsMissing() {
        VacancyContent content = VacancyFixtures.complete().build();

        assertThatCode(() -> VacancyPublishRules.requireComplete(
                content, TODAY, VacancyPublishRules.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void requireComplete_oneItem_readsAsASingleSentence() {
        VacancyContent content = VacancyFixtures.complete().jobSummary("").build();

        assertThatThrownBy(() -> VacancyPublishRules.requireComplete(
                content, TODAY, VacancyPublishRules.NOT_READY_TO_PUBLISH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job summary.");
    }

    @Test
    void requireComplete_twoItems_areJoinedWithAnd() {
        VacancyContent content = VacancyFixtures.complete().title("").jobSummary("").build();

        assertThatThrownBy(() -> VacancyPublishRules.requireComplete(
                content, TODAY, VacancyPublishRules.NOT_READY_TO_PUBLISH))
                .hasMessage("This vacancy can't be published yet: add a job title and a job summary.");
    }

    @Test
    void requireComplete_threeItems_matchTheContractsExampleSentence() {
        // The exact example from the frontend's backend-contract document.
        VacancyContent content = VacancyFixtures.complete()
                .applicationDeadline(null).jobSummary("").requiredSkills(List.of()).build();

        assertThatThrownBy(() -> VacancyPublishRules.requireComplete(
                content, TODAY, VacancyPublishRules.NOT_READY_TO_PUBLISH))
                .hasMessage("This vacancy can't be published yet: add an application deadline, "
                        + "a job summary and at least one required skill.");
    }

    @Test
    void requireComplete_usesTheLeadItIsGiven() {
        VacancyContent content = VacancyFixtures.complete().department("").build();

        assertThatThrownBy(() -> VacancyPublishRules.requireComplete(
                content, TODAY, VacancyPublishRules.MUST_STAY_COMPLETE))
                .hasMessage("A published vacancy has to stay complete: add a department.");
    }
}
