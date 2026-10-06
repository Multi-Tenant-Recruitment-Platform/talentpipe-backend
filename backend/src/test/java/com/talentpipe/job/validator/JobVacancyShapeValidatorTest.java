package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobVacancyShapeValidatorTest {

    private JobVacancyShapeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JobVacancyShapeValidator();
    }

    @Test
    void validateCreationShape_validDraft_succeeds() {
        JobVacancyRequest request = validDraftRequest();
        assertThatCode(() -> validator.validateCreationShape(request)).doesNotThrowAnyException();
    }

    @Test
    void validateCreationShape_nullStatus_throws400() {
        JobVacancyRequest request = requestWithStatus(null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Status on creation must be DRAFT or PUBLISHED.");
    }

    @Test
    void validateCreationShape_closedStatus_throws400() {
        JobVacancyRequest request = requestWithStatus(VacancyStatus.CLOSED);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Status on creation must be DRAFT or PUBLISHED.");
    }

    @Test
    void validateCreationShape_archivedStatus_throws400() {
        JobVacancyRequest request = requestWithStatus(VacancyStatus.ARCHIVED);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Status on creation must be DRAFT or PUBLISHED.");
    }

    @Test
    void validateCreationShape_titleTooLong_throws400() {
        String longTitle = "a".repeat(121);
        JobVacancyRequest request = new JobVacancyRequest(
                longTitle, "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job title must be 120 characters or fewer.");
    }

    @Test
    void validateCreationShape_locationTooLong_throws400() {
        String longLoc = "a".repeat(121);
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, longLoc, null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Location must be 120 characters or fewer.");
    }

    @Test
    void validateCreationShape_summaryTooLong_throws400() {
        String longSummary = "a".repeat(301);
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, longSummary, "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job summary must be 300 characters or fewer.");
    }

    @Test
    void validateCreationShape_descriptionTooLong_throws400() {
        String longDesc = "a".repeat(5001);
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", longDesc,
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job description must be 5000 characters or fewer.");
    }

    @Test
    void validateCreationShape_otherRequirementsTooLong_throws400() {
        String longReq = "a".repeat(1001);
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), longReq,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Other requirements must be 1000 characters or fewer.");
    }

    @Test
    void validateCreationShape_workingHoursTooLong_throws400() {
        String longHours = "a".repeat(61);
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), longHours, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Working hours must be 60 characters or fewer.");
    }

    @Test
    void validateCreationShape_openingsZeroOrNegative_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 0, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Number of openings must be between 1 and 999.");
    }

    @Test
    void validateCreationShape_openingsOver999_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1000, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Number of openings must be between 1 and 999.");
    }

    @Test
    void validateCreationShape_experienceNegative_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), -1, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Minimum experience must be between 0 and 50 years.");
    }

    @Test
    void validateCreationShape_experienceOver50_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), 51, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Minimum experience must be between 0 and 50 years.");
    }

    @Test
    void validateCreationShape_expectedHoursPerWeekZero_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, 0.0,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Expected hours per week must be between 1 and 168.");
    }

    @Test
    void validateCreationShape_expectedHoursPerWeekOver168_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, 169.0,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Expected hours per week must be between 1 and 168.");
    }

    @Test
    void validateCreationShape_salaryMinNegative_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                BigDecimal.valueOf(-10), BigDecimal.valueOf(100), "USD", PayPeriod.MONTHLY,
                List.of(), List.of(), null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Salary cannot be negative.");
    }

    @Test
    void validateCreationShape_salaryMaxNegative_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, BigDecimal.valueOf(-100), "USD", PayPeriod.MONTHLY,
                List.of(), List.of(), null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Salary cannot be negative.");
    }

    @Test
    void validateCreationShape_salaryWithoutPayPeriod_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), "USD", null,
                List.of(), List.of(), null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose whether the salary is hourly, monthly or yearly.");
    }

    @Test
    void validateCreationShape_unknownPipeline_throws422() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, "CUSTOM_PIPELINE", List.of(), VacancyStatus.DRAFT
        );
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Unknown recruitment pipeline template.");
    }

    @Test
    void validateUpdateShape_validUpdate_succeeds() {
        JobVacancyUpdateRequest request = new JobVacancyUpdateRequest(
                "Title", "Dept", 1, EmploymentType.FULL_TIME, WorkplaceType.ON_SITE, "Loc",
                LocalDate.now().plusDays(10), "Summary", "Desc", List.of("Resp"),
                List.of("Java"), List.of(), 3, null, List.of(), List.of(), null,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), "USD", PayPeriod.MONTHLY,
                List.of(), List.of(WeekDay.MON), "9-5", null, 40.0,
                null, null, "STANDARD", List.of("Question"), 0
        );
        assertThatCode(() -> validator.validateUpdateShape(request)).doesNotThrowAnyException();
    }

    private JobVacancyRequest validDraftRequest() {
        return new JobVacancyRequest(
                "Draft Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.DRAFT
        );
    }

    private JobVacancyRequest requestWithStatus(VacancyStatus status) {
        return new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "",
                List.of(), List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), status
        );
    }
}
