package com.talentpipe.job.entity;

import static com.talentpipe.job.VacancyFixtures.NOW;
import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link JobVacancy} aggregate: the lifecycle it enforces
 * and the two invariants it promises — status only follows the legal arrows,
 * and a published vacancy is always complete.
 */
class JobVacancyTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant LATER = NOW.plusSeconds(3600);

    // ---------------------------------------------------------------- creation

    @Test
    void newVacancy_isADraftWithNoLifecycleTimestamps() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete().build());

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(vacancy.getTenantId()).isEqualTo(TENANT);
        assertThat(vacancy.getVersion()).isZero();
        assertThat(vacancy.getPublishedAt()).isNull();
        assertThat(vacancy.getClosedAt()).isNull();
        assertThat(vacancy.getArchivedAt()).isNull();
    }

    @Test
    void newVacancy_mayBeCompletelyEmpty_aDraftIsAPartialVacancy() {
        assertThatCode(() -> new JobVacancy(TENANT, VacancyFixtures.blank().build()))
                .doesNotThrowAnyException();
    }

    @Test
    void newVacancy_requiresATenant() {
        VacancyContent content = VacancyFixtures.complete().build();

        assertThatThrownBy(() -> new JobVacancy(null, content))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void content_roundTripsEverythingThatWasApplied() {
        VacancyContent content = VacancyFixtures.complete()
                .salary("450000", "650000").currency("LKR — Sri Lankan rupee").payPeriod(PayPeriod.MONTHLY)
                .build();

        JobVacancy vacancy = new JobVacancy(TENANT, content);

        assertThat(vacancy.content()).isEqualTo(content);
    }

    // ----------------------------------------------------------------- publish

    @Test
    void publish_fromDraft_goesLiveAndStampsPublishedAt() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);

        vacancy.publish(NOW, TODAY);

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(vacancy.getPublishedAt()).isEqualTo(NOW);
        assertThat(vacancy.getClosedAt()).isNull();
    }

    @Test
    void publish_incompleteDraft_isRefusedAndStaysADraft() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete()
                .applicationDeadline(null).requiredSkills(List.of()).build());

        assertThatThrownBy(() -> vacancy.publish(NOW, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add an application deadline "
                        + "and at least one required skill.");

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(vacancy.getPublishedAt()).isNull();
    }

    @Test
    void publish_withDeadlineToday_isAllowed_theDeadlineDayStillCounts() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete().applicationDeadline(TODAY).build());

        vacancy.publish(NOW, TODAY);

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
    }

    @Test
    void publish_withPassedDeadline_isRefused() {
        JobVacancy vacancy = new JobVacancy(TENANT,
                VacancyFixtures.complete().applicationDeadline(TODAY.minusDays(1)).build());

        assertThatThrownBy(() -> vacancy.publish(NOW, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("an application deadline that is today or later");
    }

    @Test
    void publish_alreadyPublished_isRefusedAsAlreadyPublished() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);

        assertThatThrownBy(() -> vacancy.publish(LATER, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a draft vacancy can be published. This vacancy is already published.");

        assertThat(vacancy.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void publish_closedVacancy_isRefusedForBeingClosed_notForBeingIncomplete() {
        // The transition is checked before completeness. This closed vacancy's
        // deadline has long passed; the recruiter should still be told the
        // real reason, not sent to fix a date on something that cannot reopen.
        JobVacancy vacancy = VacancyFixtures.closed(TENANT);

        assertThatThrownBy(() -> vacancy.publish(LATER, TODAY.plusYears(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a draft vacancy can be published. This vacancy is closed.");
    }

    // ------------------------------------------------------------------- close

    @Test
    void close_fromPublished_closesAndStampsClosedAt_keepingPublishedAt() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);

        vacancy.close(LATER);

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.CLOSED);
        assertThat(vacancy.getClosedAt()).isEqualTo(LATER);
        assertThat(vacancy.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void close_draft_isRefused() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);

        assertThatThrownBy(() -> vacancy.close(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a published vacancy can be closed. This vacancy is a draft.");

        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(vacancy.getClosedAt()).isNull();
    }

    @Test
    void close_alreadyClosed_isRefused() {
        JobVacancy vacancy = VacancyFixtures.closed(TENANT);

        assertThatThrownBy(() -> vacancy.close(LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a published vacancy can be closed. This vacancy is already closed.");
    }

    // ----------------------------------------------------------------- archive

    @Test
    void archive_fromClosed_archivesAndKeepsTheWholeHistory() {
        JobVacancy vacancy = VacancyFixtures.closed(TENANT);

        vacancy.archive(LATER);

        // Reporting retention: every lifecycle timestamp survives archiving.
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.ARCHIVED);
        assertThat(vacancy.getArchivedAt()).isEqualTo(LATER);
        assertThat(vacancy.getPublishedAt()).isEqualTo(NOW);
        assertThat(vacancy.getClosedAt()).isEqualTo(NOW);
        assertThat(vacancy.content()).isEqualTo(VacancyFixtures.complete().build());
    }

    @Test
    void archive_publishedVacancy_isRefused_itMustBeClosedFirst() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);

        assertThatThrownBy(() -> vacancy.archive(LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a closed vacancy can be archived. This vacancy is published.");
    }

    @Test
    void archive_draft_isRefused() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);

        assertThatThrownBy(() -> vacancy.archive(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("Only a closed vacancy can be archived.");
    }

    @Test
    void archived_isTerminal_everyMoveIsRefused() {
        JobVacancy vacancy = VacancyFixtures.archived(TENANT);

        assertThatThrownBy(() -> vacancy.publish(LATER, TODAY)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> vacancy.close(LATER)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> vacancy.archive(LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageEndingWith("This vacancy is already archived.");
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.ARCHIVED);
    }

    // -------------------------------------------------------------------- edit

    @Test
    void applyContent_onDraft_acceptsIncompleteContent() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);
        VacancyContent emptied = VacancyFixtures.blank().build();

        vacancy.applyContent(emptied, TODAY);

        assertThat(vacancy.content()).isEqualTo(emptied);
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.DRAFT);
    }

    @Test
    void applyContent_onPublished_replacesContentAndNeverTouchesStatus() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);
        VacancyContent edited = VacancyFixtures.complete().title("Staff Backend Engineer").build();

        vacancy.applyContent(edited, TODAY);

        assertThat(vacancy.content().title()).isEqualTo("Staff Backend Engineer");
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(vacancy.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void applyContent_onPublished_refusesAnEditThatWouldLeaveItIncomplete() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);
        VacancyContent incomplete = VacancyFixtures.complete().jobSummary("").build();

        assertThatThrownBy(() -> vacancy.applyContent(incomplete, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A published vacancy has to stay complete: add a job summary.");

        // The refused edit must not have been half-applied.
        assertThat(vacancy.content().jobSummary()).isNotEmpty();
    }

    @Test
    void applyContent_onClosed_isRefused() {
        JobVacancy vacancy = VacancyFixtures.closed(TENANT);
        VacancyContent edited = VacancyFixtures.complete().title("Rewritten").build();

        assertThatThrownBy(() -> vacancy.applyContent(edited, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy is closed and can no longer be edited.");

        assertThat(vacancy.content().title()).isEqualTo("Senior Backend Engineer");
    }

    @Test
    void applyContent_onArchived_isRefused() {
        JobVacancy vacancy = VacancyFixtures.archived(TENANT);
        VacancyContent edited = VacancyFixtures.complete().build();

        assertThatThrownBy(() -> vacancy.applyContent(edited, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy is archived and can no longer be edited.");
    }

    // ------------------------------------------------------------ applications

    @Test
    void acceptsApplications_onlyWhilePublishedAndUpToTheDeadlineDay() {
        JobVacancy vacancy = new JobVacancy(TENANT, VacancyFixtures.complete().applicationDeadline(TODAY).build());
        assertThat(vacancy.isAcceptingApplications(TODAY)).as("draft").isFalse();

        vacancy.publish(NOW, TODAY);
        assertThat(vacancy.isAcceptingApplications(TODAY)).as("published, deadline today").isTrue();
        assertThat(vacancy.isAcceptingApplications(TODAY.plusDays(1))).as("published, deadline passed").isFalse();

        vacancy.close(LATER);
        assertThat(vacancy.isAcceptingApplications(TODAY)).as("closed").isFalse();

        vacancy.archive(LATER);
        assertThat(vacancy.isAcceptingApplications(TODAY)).as("archived").isFalse();
    }

    // ------------------------------------------------------------------- other

    @Test
    void toString_carriesIdentifiersAndStateOnly_neverAdvertText() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);

        assertThat(vacancy.toString())
                .contains(TENANT.toString(), "DRAFT")
                .doesNotContain("Senior Backend Engineer");
    }
}
