package com.talentpipe.job.entity;

import static com.talentpipe.job.entity.VacancyStatus.ARCHIVED;
import static com.talentpipe.job.entity.VacancyStatus.CLOSED;
import static com.talentpipe.job.entity.VacancyStatus.DRAFT;
import static com.talentpipe.job.entity.VacancyStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the vacancy state machine. The whole lifecycle feature rests on this
 * table, so it is checked exhaustively — all sixteen (from, to) pairs — rather
 * than by a few examples.
 */
class VacancyStatusTest {

    /** The agreed lifecycle, written out independently of the enum's own table. */
    private static final Map<VacancyStatus, Set<VacancyStatus>> EXPECTED = Map.of(
            DRAFT, Set.of(PUBLISHED),
            PUBLISHED, Set.of(CLOSED),
            CLOSED, Set.of(ARCHIVED),
            ARCHIVED, Set.of());

    @ParameterizedTest
    @EnumSource(VacancyStatus.class)
    void everyPairOfStatesIsAllowedOrRefusedAsAgreed(VacancyStatus from) {
        for (VacancyStatus to : VacancyStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(VacancyStatus.class)
    void nextStatusesMatchTheTable(VacancyStatus from) {
        assertThat(from.nextStatuses()).isEqualTo(EXPECTED.get(from));
    }

    @ParameterizedTest
    @EnumSource(VacancyStatus.class)
    void noStateCanTransitionToItself(VacancyStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void theMachineIsOneWay_nothingLeadsBackToAnEarlierState() {
        for (VacancyStatus from : VacancyStatus.values()) {
            for (VacancyStatus to : VacancyStatus.values()) {
                if (to.ordinal() < from.ordinal()) {
                    assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isFalse();
                }
            }
        }
    }

    @Test
    void archivedIsTheOnlyTerminalState() {
        assertThat(ARCHIVED.nextStatuses()).isEmpty();
        assertThat(DRAFT.nextStatuses()).isNotEmpty();
        assertThat(PUBLISHED.nextStatuses()).isNotEmpty();
        assertThat(CLOSED.nextStatuses()).isNotEmpty();
    }

    @Test
    void contentIsEditableOnlyWhileDraftOrPublished() {
        assertThat(DRAFT.isEditable()).isTrue();
        assertThat(PUBLISHED.isEditable()).isTrue();
        assertThat(CLOSED.isEditable()).isFalse();
        assertThat(ARCHIVED.isEditable()).isFalse();
    }

    @Test
    void onlyPublishedIsOnThePublicPortal() {
        assertThat(DRAFT.isPubliclyListed()).isFalse();
        assertThat(PUBLISHED.isPubliclyListed()).isTrue();
        assertThat(CLOSED.isPubliclyListed()).isFalse();
        assertThat(ARCHIVED.isPubliclyListed()).isFalse();
    }

    @Test
    void entryRulesNameTheStateAVacancyMustComeFrom() {
        assertThat(PUBLISHED.entryRule()).isEqualTo("Only a draft vacancy can be published.");
        assertThat(CLOSED.entryRule()).isEqualTo("Only a published vacancy can be closed.");
        assertThat(ARCHIVED.entryRule()).isEqualTo("Only a closed vacancy can be archived.");
    }

    @Test
    void entryRuleForAStateNothingLeadsToSaysSo() {
        assertThat(DRAFT.entryRule()).isEqualTo("A vacancy can't be moved back to draft.");
    }

    @Test
    void descriptionsReadNaturallyAfterThisVacancyIs() {
        assertThat(DRAFT.description()).isEqualTo("a draft");
        assertThat(PUBLISHED.description()).isEqualTo("published");
        assertThat(CLOSED.description()).isEqualTo("closed");
        assertThat(ARCHIVED.description()).isEqualTo("archived");
    }
}
