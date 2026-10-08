package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobVacancyPublishValidatorTest {

    private JobVacancyPublishValidator validator;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        validator = new JobVacancyPublishValidator();
        today = LocalDate.of(2026, 10, 2);
    }

    @Test
    void validateForPublish_allFieldsValid_succeeds() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .applicationDeadline(today.plusDays(7))
                .toRequest(VacancyStatus.PUBLISHED, null);
        assertThatCode(() -> validator.validateForPublish(request, today)).doesNotThrowAnyException();
    }

    @Test
    void validateForPublish_deadlineToday_succeeds() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .applicationDeadline(today)
                .toRequest(VacancyStatus.PUBLISHED, null);
        assertThatCode(() -> validator.validateForPublish(request, today)).doesNotThrowAnyException();
    }

    @Test
    void validateForPublish_deadlineYesterday_throws422() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .applicationDeadline(today.minusDays(1))
                .toRequest(VacancyStatus.PUBLISHED, null);
        assertThatThrownBy(() -> validator.validateForPublish(request, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add an application deadline that is today or later.");
    }

    @Test
    void validateForPublish_missingJobSummaryAndRequiredSkills_throws422WithTwoItems() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .applicationDeadline(today.plusDays(5))
                .jobSummary("  ")
                .requiredSkills(List.of("  "))
                .toRequest(VacancyStatus.PUBLISHED, null);

        assertThatThrownBy(() -> validator.validateForPublish(request, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job summary and at least one required skill.");
    }

    @Test
    void validateForPublish_emptyEntity_throws422ListingMissingBasics() {
        JobVacancy entity = new JobVacancy(java.util.UUID.randomUUID(), VacancyFixtures.blank().build());
        assertThatThrownBy(() -> validator.validateForPublish(entity, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("This vacancy can't be published yet: add ");
    }

    @Test
    void validateForStayComplete_incompleteRequest_usesStayCompleteLead() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .jobSummary("")
                .toRequest(VacancyStatus.PUBLISHED, 1);

        assertThatThrownBy(() -> validator.validateForStayComplete(request, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A published vacancy has to stay complete: add a job summary.");
    }

    @Test
    void requireComplete_staticMethod_passesWhenComplete() {
        VacancyContent content = VacancyFixtures.complete().build();
        assertThatCode(() -> JobVacancyPublishValidator.requireComplete(content, today, JobVacancyPublishValidator.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void joinLabels_helperTests() {
        assertThat(JobVacancyPublishValidator.joinLabels(List.of())).isEmpty();
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1"))).isEqualTo("item1");
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1", "item2"))).isEqualTo("item1 and item2");
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1", "item2", "item3"))).isEqualTo("item1, item2 and item3");
    }
}
