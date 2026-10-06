package com.talentpipe.job.entity;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;

class JobVacancyEntityTest {

    @Test
    void settersAndGetters_workWithValuesAndNullFallbacks() {
        JobVacancy vacancy = new JobVacancy();
        UUID tenantId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate deadline = LocalDate.of(2026, 12, 31);

        vacancy.setTenantId(tenantId);
        vacancy.setVersion(2);
        vacancy.setStatus(VacancyStatus.PUBLISHED);
        vacancy.setTitle(null);
        vacancy.setDepartment(null);
        vacancy.setOpenings(5);
        vacancy.setEmploymentType(EmploymentType.FULL_TIME);
        vacancy.setWorkplaceType(WorkplaceType.HYBRID);
        vacancy.setLocation(null);
        vacancy.setApplicationDeadline(deadline);
        vacancy.setJobSummary(null);
        vacancy.setJobDescription(null);
        vacancy.setKeyResponsibilities(null);
        vacancy.setRequiredSkills(null);
        vacancy.setPreferredSkills(null);
        vacancy.setMinimumExperienceYears(3);
        vacancy.setEducation("BSc");
        vacancy.setCertifications(null);
        vacancy.setLanguageRequirements(null);
        vacancy.setOtherRequirements("Requirements");
        vacancy.setSalaryMin(BigDecimal.valueOf(1000));
        vacancy.setSalaryMax(BigDecimal.valueOf(2000));
        vacancy.setCurrency("USD");
        vacancy.setPayPeriod(PayPeriod.MONTHLY);
        vacancy.setBenefits(null);
        vacancy.setWorkingDays(null);
        vacancy.setWorkingHours("8:00 - 17:00");
        vacancy.setShiftType(ShiftType.DAY);
        vacancy.setExpectedHoursPerWeek(40.0);
        vacancy.setAssignedRecruiterId(recruiterId);
        vacancy.setHiringManagerId(managerId);
        vacancy.setRecruitmentPipelineId("PIPELINE_1");
        vacancy.setScreeningQuestions(null);
        vacancy.setPublishedAt(now);
        vacancy.setClosedAt(now);
        vacancy.setArchivedAt(now);

        assertThat(vacancy.getTenantId()).isEqualTo(tenantId);
        assertThat(vacancy.getVersion()).isEqualTo(2);
        assertThat(vacancy.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(vacancy.getTitle()).isEmpty();
        assertThat(vacancy.getDepartment()).isEmpty();
        assertThat(vacancy.getOpenings()).isEqualTo(5);
        assertThat(vacancy.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(vacancy.getWorkplaceType()).isEqualTo(WorkplaceType.HYBRID);
        assertThat(vacancy.getLocation()).isEmpty();
        assertThat(vacancy.getApplicationDeadline()).isEqualTo(deadline);
        assertThat(vacancy.getJobSummary()).isEmpty();
        assertThat(vacancy.getJobDescription()).isEmpty();
        assertThat(vacancy.getKeyResponsibilities()).isEmpty();
        assertThat(vacancy.getRequiredSkills()).isEmpty();
        assertThat(vacancy.getPreferredSkills()).isEmpty();
        assertThat(vacancy.getMinimumExperienceYears()).isEqualTo(3);
        assertThat(vacancy.getEducation()).isEqualTo("BSc");
        assertThat(vacancy.getCertifications()).isEmpty();
        assertThat(vacancy.getLanguageRequirements()).isEmpty();
        assertThat(vacancy.getOtherRequirements()).isEqualTo("Requirements");
        assertThat(vacancy.getSalaryMin()).isEqualByComparingTo("1000");
        assertThat(vacancy.getSalaryMax()).isEqualByComparingTo("2000");
        assertThat(vacancy.getCurrency()).isEqualTo("USD");
        assertThat(vacancy.getPayPeriod()).isEqualTo(PayPeriod.MONTHLY);
        assertThat(vacancy.getBenefits()).isEmpty();
        assertThat(vacancy.getWorkingDays()).isEmpty();
        assertThat(vacancy.getWorkingHours()).isEqualTo("8:00 - 17:00");
        assertThat(vacancy.getShiftType()).isEqualTo(ShiftType.DAY);
        assertThat(vacancy.getExpectedHoursPerWeek()).isEqualTo(40.0);
        assertThat(vacancy.getAssignedRecruiterId()).isEqualTo(recruiterId);
        assertThat(vacancy.getHiringManagerId()).isEqualTo(managerId);
        assertThat(vacancy.getRecruitmentPipelineId()).isEqualTo("PIPELINE_1");
        assertThat(vacancy.getScreeningQuestions()).isEmpty();
        assertThat(vacancy.getPublishedAt()).isEqualTo(now);
        assertThat(vacancy.getClosedAt()).isEqualTo(now);
        assertThat(vacancy.getArchivedAt()).isEqualTo(now);

        // Test non-null setters
        vacancy.setTitle("Title");
        vacancy.setDepartment("Dept");
        vacancy.setLocation("Colombo");
        vacancy.setJobSummary("Summary");
        vacancy.setJobDescription("Desc");
        vacancy.setKeyResponsibilities(List.of("R1"));
        vacancy.setRequiredSkills(List.of("S1"));
        vacancy.setPreferredSkills(List.of("P1"));
        vacancy.setCertifications(List.of("C1"));
        vacancy.setLanguageRequirements(List.of("L1"));
        vacancy.setBenefits(List.of("B1"));
        vacancy.setWorkingDays(List.of(WeekDay.MON));
        vacancy.setScreeningQuestions(List.of("Q1"));

        assertThat(vacancy.getTitle()).isEqualTo("Title");
        assertThat(vacancy.getDepartment()).isEqualTo("Dept");
        assertThat(vacancy.getLocation()).isEqualTo("Colombo");
        assertThat(vacancy.getJobSummary()).isEqualTo("Summary");
        assertThat(vacancy.getJobDescription()).isEqualTo("Desc");
        assertThat(vacancy.getKeyResponsibilities()).containsExactly("R1");
        assertThat(vacancy.getRequiredSkills()).containsExactly("S1");
        assertThat(vacancy.getPreferredSkills()).containsExactly("P1");
        assertThat(vacancy.getCertifications()).containsExactly("C1");
        assertThat(vacancy.getLanguageRequirements()).containsExactly("L1");
        assertThat(vacancy.getBenefits()).containsExactly("B1");
        assertThat(vacancy.getWorkingDays()).containsExactly(WeekDay.MON);
        assertThat(vacancy.getScreeningQuestions()).containsExactly("Q1");
    }
}
