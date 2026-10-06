package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import java.math.BigDecimal;
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
        JobVacancyRequest request = validPublishedRequest(today.plusDays(7));
        assertThatCode(() -> validator.validateForPublish(request, today)).doesNotThrowAnyException();
    }

    @Test
    void validateForPublish_deadlineToday_succeeds() {
        JobVacancyRequest request = validPublishedRequest(today);
        assertThatCode(() -> validator.validateForPublish(request, today)).doesNotThrowAnyException();
    }

    @Test
    void validateForPublish_deadlineYesterday_throws422() {
        JobVacancyRequest request = validPublishedRequest(today.minusDays(1));
        assertThatThrownBy(() -> validator.validateForPublish(request, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add an application deadline.");
    }

    @Test
    void validateForPublish_missingJobSummaryAndRequiredSkills_throws422WithTwoItems() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, EmploymentType.FULL_TIME, WorkplaceType.ON_SITE,
                "Colombo", today.plusDays(5), "  ", "Description", List.of("Resp"),
                List.of("  "), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.PUBLISHED
        );
        assertThatThrownBy(() -> validator.validateForPublish(request, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job summary and at least one required skill.");
    }

    @Test
    void validateForPublish_emptyEntity_throws422ListingMissingBasics() {
        JobVacancy entity = new JobVacancy();
        assertThatThrownBy(() -> validator.validateForPublish(entity, today))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("This vacancy can't be published yet: add ");
    }

    @Test
    void validateForPublish_updateRequest_succeedsWhenComplete() {
        JobVacancyUpdateRequest updateRequest = new JobVacancyUpdateRequest(
                "Title", "Dept", 1, EmploymentType.FULL_TIME, WorkplaceType.ON_SITE,
                "Colombo", today.plusDays(5), "Summary", "Description", List.of("Resp"),
                List.of("Skill"), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), 0
        );
        assertThatCode(() -> validator.validateForPublish(updateRequest, today)).doesNotThrowAnyException();
    }

    @Test
    void joinLabels_helperTests() {
        assertThat(JobVacancyPublishValidator.joinLabels(List.of())).isEmpty();
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1"))).isEqualTo("item1");
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1", "item2"))).isEqualTo("item1 and item2");
        assertThat(JobVacancyPublishValidator.joinLabels(List.of("item1", "item2", "item3"))).isEqualTo("item1, item2 and item3");
    }

    private JobVacancyRequest validPublishedRequest(LocalDate deadline) {
        return new JobVacancyRequest(
                "Senior Engineer", "Engineering", 2, EmploymentType.FULL_TIME, WorkplaceType.REMOTE,
                "Colombo", deadline, "Summary here", "Full description here",
                List.of("Key responsibility 1"), List.of("Java"), List.of(), 3, "Degree",
                List.of(), List.of(), null, BigDecimal.valueOf(3000), BigDecimal.valueOf(5000),
                "USD", PayPeriod.MONTHLY, List.of(), List.of(WeekDay.MON), "9-5",
                null, 40.0, null, null, "STANDARD", List.of(), VacancyStatus.PUBLISHED
        );
    }
}
