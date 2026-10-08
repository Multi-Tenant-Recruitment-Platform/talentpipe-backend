package com.talentpipe.job.entity;

import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.job.VacancyFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VacancyContent}: the shape guarantees of the value
 * object and the duplicate template (PB-021).
 */
class VacancyContentTest {

    // ------------------------------------------------------------------- shape

    @Test
    void nullTextBasics_becomeEmptyStrings_matchingTheNotNullColumns() {
        VacancyContent content = VacancyFixtures.complete()
                .title(null).department(null).location(null).jobSummary(null).jobDescription(null)
                .build();

        assertThat(content.title()).isEmpty();
        assertThat(content.department()).isEmpty();
        assertThat(content.location()).isEmpty();
        assertThat(content.jobSummary()).isEmpty();
        assertThat(content.jobDescription()).isEmpty();
    }

    @Test
    void nullLists_becomeEmptyLists() {
        VacancyContent content = VacancyFixtures.complete()
                .keyResponsibilities(null).requiredSkills(null).benefits(null).workingDays(null)
                .build();

        assertThat(content.keyResponsibilities()).isEmpty();
        assertThat(content.requiredSkills()).isEmpty();
        assertThat(content.benefits()).isEmpty();
        assertThat(content.workingDays()).isEmpty();
    }

    @Test
    void lists_areCopiedAndImmutable_soContentCannotChangeAfterTheFact() {
        List<String> skills = new ArrayList<>(List.of("Java"));
        VacancyContent content = VacancyFixtures.complete().requiredSkills(skills).build();

        skills.add("Kotlin");

        assertThat(content.requiredSkills()).containsExactly("Java");
        List<String> stored = content.requiredSkills();
        assertThatThrownBy(() -> stored.add("Go")).isInstanceOf(UnsupportedOperationException.class);
    }

    // --------------------------------------------------------------- duplicate

    @Test
    void asDuplicate_marksTheTitleAsACopy() {
        VacancyContent copy = VacancyFixtures.complete().build().asDuplicate(id -> true);

        assertThat(copy.title()).isEqualTo("Senior Backend Engineer (copy)");
    }

    @Test
    void asDuplicate_clearsTheDeadline_soACopiedDateIsNeverPublishedUnchecked() {
        VacancyContent source = VacancyFixtures.complete().applicationDeadline(TODAY.plusDays(10)).build();

        assertThat(source.asDuplicate(id -> true).applicationDeadline()).isNull();
    }

    @Test
    void asDuplicate_carriesOverEverythingElseTheRecruiterWrote() {
        VacancyContent source = VacancyFixtures.complete()
                .salary("450000", "650000").currency("LKR — Sri Lankan rupee").payPeriod(PayPeriod.MONTHLY)
                .build();

        VacancyContent copy = source.asDuplicate(id -> true);

        assertThat(copy)
                .usingRecursiveComparison()
                .ignoringFields("title", "applicationDeadline")
                .isEqualTo(source);
    }

    @Test
    void asDuplicate_ofAnUntitledDraft_staysUntitled() {
        VacancyContent copy = VacancyFixtures.blank().build().asDuplicate(id -> true);

        assertThat(copy.title()).isEmpty();
    }

    @Test
    void asDuplicate_shortensALongTitleSoTheCopySuffixStillFitsTheColumn() {
        String longTitle = "T".repeat(VacancyContent.MAX_TITLE_LENGTH);

        VacancyContent copy = VacancyFixtures.complete().title(longTitle).build().asDuplicate(id -> true);

        assertThat(copy.title())
                .hasSize(VacancyContent.MAX_TITLE_LENGTH)
                .endsWith(" (copy)");
    }

    @Test
    void asDuplicate_keepsAssigneesWhoCanStillBeAssigned() {
        UUID recruiter = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        VacancyContent source = VacancyFixtures.complete()
                .assignedRecruiterId(recruiter).hiringManagerId(manager).build();

        VacancyContent copy = source.asDuplicate(id -> true);

        assertThat(copy.assignedRecruiterId()).isEqualTo(recruiter);
        assertThat(copy.hiringManagerId()).isEqualTo(manager);
    }

    @Test
    void asDuplicate_dropsAnAssigneeWhoHasLeftTheWorkspace() {
        UUID departed = UUID.randomUUID();
        UUID stillHere = UUID.randomUUID();
        VacancyContent source = VacancyFixtures.complete()
                .assignedRecruiterId(departed).hiringManagerId(stillHere).build();

        VacancyContent copy = source.asDuplicate(stillHere::equals);

        assertThat(copy.assignedRecruiterId()).isNull();
        assertThat(copy.hiringManagerId()).isEqualTo(stillHere);
    }

    @Test
    void asDuplicate_leavesTheSourceUntouched() {
        VacancyContent source = VacancyFixtures.complete().build();

        source.asDuplicate(id -> true);

        assertThat(source.title()).isEqualTo("Senior Backend Engineer");
        assertThat(source.applicationDeadline()).isNotNull();
    }
}
