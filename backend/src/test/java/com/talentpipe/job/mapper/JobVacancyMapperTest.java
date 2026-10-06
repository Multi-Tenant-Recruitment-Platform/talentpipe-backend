package com.talentpipe.job.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.ShiftType;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobVacancyMapperTest {

    private JobVacancyMapper mapper;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mapper = new JobVacancyMapper();
        tenantId = UUID.randomUUID();
    }

    @Test
    void toEntity_mapsAllFieldsAndCopiesLists() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Senior Dev", "Engineering", 3, EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                "Colombo", LocalDate.of(2026, 12, 1), "Summary", "Description",
                List.of("Resp 1"), List.of("Java"), List.of("Spring"), 5, "BSc",
                List.of("Cert 1"), List.of("English"), "Other reqs",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(4000), "USD", PayPeriod.MONTHLY,
                List.of("Health"), List.of(WeekDay.MON, WeekDay.TUE), "9-5", ShiftType.DAY,
                40.0, UUID.randomUUID(), UUID.randomUUID(), "STANDARD",
                List.of("Question 1"), VacancyStatus.DRAFT
        );

        JobVacancy entity = mapper.toEntity(tenantId, request);

        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getStatus()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(entity.getTitle()).isEqualTo("Senior Dev");
        assertThat(entity.getDepartment()).isEqualTo("Engineering");
        assertThat(entity.getOpenings()).isEqualTo(3);
        assertThat(entity.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(entity.getWorkplaceType()).isEqualTo(WorkplaceType.HYBRID);
        assertThat(entity.getLocation()).isEqualTo("Colombo");
        assertThat(entity.getApplicationDeadline()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(entity.getJobSummary()).isEqualTo("Summary");
        assertThat(entity.getJobDescription()).isEqualTo("Description");
        assertThat(entity.getKeyResponsibilities()).containsExactly("Resp 1");
        assertThat(entity.getRequiredSkills()).containsExactly("Java");
        assertThat(entity.getPreferredSkills()).containsExactly("Spring");
        assertThat(entity.getMinimumExperienceYears()).isEqualTo(5);
        assertThat(entity.getEducation()).isEqualTo("BSc");
        assertThat(entity.getCertifications()).containsExactly("Cert 1");
        assertThat(entity.getLanguageRequirements()).containsExactly("English");
        assertThat(entity.getOtherRequirements()).isEqualTo("Other reqs");
        assertThat(entity.getSalaryMin()).isEqualByComparingTo("2000");
        assertThat(entity.getSalaryMax()).isEqualByComparingTo("4000");
        assertThat(entity.getCurrency()).isEqualTo("USD");
        assertThat(entity.getPayPeriod()).isEqualTo(PayPeriod.MONTHLY);
        assertThat(entity.getBenefits()).containsExactly("Health");
        assertThat(entity.getWorkingDays()).containsExactly(WeekDay.MON, WeekDay.TUE);
        assertThat(entity.getWorkingHours()).isEqualTo("9-5");
        assertThat(entity.getShiftType()).isEqualTo(ShiftType.DAY);
        assertThat(entity.getExpectedHoursPerWeek()).isEqualTo(40.0);
        assertThat(entity.getAssignedRecruiterId()).isEqualTo(request.assignedRecruiterId());
        assertThat(entity.getHiringManagerId()).isEqualTo(request.hiringManagerId());
        assertThat(entity.getRecruitmentPipelineId()).isEqualTo("STANDARD");
        assertThat(entity.getScreeningQuestions()).containsExactly("Question 1");
    }

    @Test
    void toEntity_nullListFields_fallBackToEmptyLists() {
        JobVacancyRequest request = new JobVacancyRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, VacancyStatus.DRAFT
        );

        JobVacancy entity = mapper.toEntity(tenantId, request);

        assertThat(entity.getTitle()).isEmpty();
        assertThat(entity.getDepartment()).isEmpty();
        assertThat(entity.getLocation()).isEmpty();
        assertThat(entity.getJobSummary()).isEmpty();
        assertThat(entity.getJobDescription()).isEmpty();
        assertThat(entity.getKeyResponsibilities()).isEmpty();
        assertThat(entity.getRequiredSkills()).isEmpty();
        assertThat(entity.getPreferredSkills()).isEmpty();
        assertThat(entity.getCertifications()).isEmpty();
        assertThat(entity.getLanguageRequirements()).isEmpty();
        assertThat(entity.getBenefits()).isEmpty();
        assertThat(entity.getWorkingDays()).isEmpty();
        assertThat(entity.getScreeningQuestions()).isEmpty();
        assertThat(entity.getOpenings()).isEqualTo(1);
    }

    @Test
    void updateEntity_modifiesContentWithoutAlteringStatus() {
        JobVacancy entity = new JobVacancy();
        entity.setStatus(VacancyStatus.PUBLISHED);

        JobVacancyUpdateRequest update = new JobVacancyUpdateRequest(
                "New Title", "New Dept", 5, EmploymentType.PART_TIME, WorkplaceType.REMOTE,
                "New Loc", LocalDate.of(2027, 1, 1), "New Summary", "New Desc",
                List.of("New Resp"), List.of("React"), List.of(), 1, "Diploma",
                List.of(), List.of(), null, null, null, null, null,
                List.of(), List.of(WeekDay.WED), "Part time", ShiftType.FLEXIBLE, 20.0,
                null, null, "TECHNICAL", List.of(), 1
        );

        mapper.updateEntity(entity, update);

        assertThat(entity.getTitle()).isEqualTo("New Title");
        assertThat(entity.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(entity.getOpenings()).isEqualTo(5);
        assertThat(entity.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
        assertThat(entity.getWorkplaceType()).isEqualTo(WorkplaceType.REMOTE);
    }

    @Test
    void toResponse_mapsFieldsCorrectly() {
        JobVacancy entity = new JobVacancy();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-10-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-10-01T01:00:00Z"));
        entity.setStatus(VacancyStatus.CLOSED);
        entity.setTitle("Lead Architect");
        entity.setClosedAt(Instant.parse("2026-10-02T00:00:00Z"));

        JobVacancyResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(VacancyStatus.CLOSED);
        assertThat(response.title()).isEqualTo("Lead Architect");
        assertThat(response.closedAt()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(response.applicantCount()).isNull();
    }
}
