package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.VacancyStatus;
import java.math.BigDecimal;
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
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(VacancyStatus.DRAFT, null);
        assertThatCode(() -> validator.validateCreationShape(request)).doesNotThrowAnyException();
    }

    @Test
    void validateCreationShape_nullStatus_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(null, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A new vacancy can only be saved as a draft or published.");
    }

    @Test
    void validateCreationShape_closedStatus_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(VacancyStatus.CLOSED, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A new vacancy can only be saved as a draft or published.");
    }

    @Test
    void validateCreationShape_archivedStatus_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(VacancyStatus.ARCHIVED, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A new vacancy can only be saved as a draft or published.");
    }

    @Test
    void validateCreationShape_titleTooLong_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .title("a".repeat(121))
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job title must be 120 characters or fewer.");
    }

    @Test
    void validateCreationShape_locationTooLong_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .location("a".repeat(121))
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Location must be 120 characters or fewer.");
    }

    @Test
    void validateCreationShape_summaryTooLong_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .jobSummary("a".repeat(301))
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job summary must be 300 characters or fewer.");
    }

    @Test
    void validateCreationShape_descriptionTooLong_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .jobDescription("a".repeat(5001))
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Job description must be 5000 characters or fewer.");
    }

    @Test
    void validateCreationShape_openingsZeroOrNegative_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .openings(0)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Number of openings must be between 1 and 999.");
    }

    @Test
    void validateCreationShape_openingsOver999_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .openings(1000)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Number of openings must be between 1 and 999.");
    }

    @Test
    void validateCreationShape_salaryMinNegative_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .salary("-10", "100")
                .currency("USD")
                .payPeriod(PayPeriod.MONTHLY)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Salary cannot be negative.");
    }

    @Test
    void validateCreationShape_salaryMaxNegative_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .salary(null, "-100")
                .currency("USD")
                .payPeriod(PayPeriod.MONTHLY)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Salary cannot be negative.");
    }

    @Test
    void validateCreationShape_salaryMaxLessThanMin_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .salary("5000", "2000")
                .currency("USD")
                .payPeriod(PayPeriod.MONTHLY)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Maximum salary must be at least the minimum salary.");
    }

    @Test
    void validateCreationShape_salaryWithoutCurrency_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .salary("1000", "2000")
                .currency(null)
                .payPeriod(PayPeriod.MONTHLY)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose a currency for the salary range you entered.");
    }

    @Test
    void validateCreationShape_salaryWithoutPayPeriod_throws400() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .salary("1000", "2000")
                .currency("USD")
                .payPeriod(null)
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose whether the salary is hourly, monthly or yearly.");
    }

    @Test
    void validateCreationShape_unknownPipeline_throws422() {
        JobVacancyRequest request = VacancyFixtures.blank()
                .recruitmentPipelineId("CUSTOM_PIPELINE")
                .toRequest(VacancyStatus.DRAFT, null);
        assertThatThrownBy(() -> validator.validateCreationShape(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Unknown recruitment pipeline template.");
    }

    @Test
    void validateUpdateShape_validUpdate_succeeds() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, 1);
        assertThatCode(() -> validator.validateUpdateShape(request)).doesNotThrowAnyException();
    }
}
