package com.talentpipe.job.mapper;

import static com.talentpipe.job.VacancyFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JobVacancyMapper}. Beyond field copying, these pin the
 * two things the mapping is responsible for: the recruiter view matches the
 * frontend contract, and the public view leaks nothing internal.
 */
class JobVacancyMapperTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final CompanySummaryResponse ACME = new CompanySummaryResponse(
            TENANT, "Acme Corp", "acme", "https://cdn.example.com/acme.png", "Asia/Colombo");

    private final JobVacancyMapper mapper = new JobVacancyMapper();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---------------------------------------------------------- recruiter view

    @Test
    void toResponse_carriesContentLifecycleAndVersion() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);
        VacancyContent content = vacancy.content();

        JobVacancyResponse response = mapper.toResponse(vacancy);

        assertThat(response.id()).isEqualTo(vacancy.getId());
        assertThat(response.version()).isZero();
        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.title()).isEqualTo(content.title());
        assertThat(response.requiredSkills()).isEqualTo(content.requiredSkills());
        assertThat(response.workingDays()).isEqualTo(content.workingDays());
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.publishedAt()).isEqualTo(NOW);
        assertThat(response.closedAt()).isNull();
        assertThat(response.archivedAt()).isNull();
    }

    @Test
    void toResponse_applicantCountIsNull_notZero_untilTheApplicationsModuleExists() {
        assertThat(mapper.toResponse(VacancyFixtures.draft(TENANT)).applicantCount()).isNull();
    }

    @Test
    void toResponse_neverSerializesATenantId() {
        JsonNode body = json.valueToTree(mapper.toResponse(VacancyFixtures.draft(TENANT)));

        assertThat(fieldNames(body)).doesNotContain("tenantId");
    }

    // ------------------------------------------------------------- public view

    @Test
    void toSummary_addsCompanyBrandingAndAStableSlug() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);

        JobSummaryResponse summary = mapper.toSummary(vacancy, ACME, true);

        assertThat(summary.id()).isEqualTo(vacancy.getId());
        assertThat(summary.companyName()).isEqualTo("Acme Corp");
        assertThat(summary.companyLogoUrl()).isEqualTo("https://cdn.example.com/acme.png");
        assertThat(summary.slug()).startsWith("senior-backend-engineer-acme-corp-");
        assertThat(JobSlug.resolveId(summary.slug())).contains(vacancy.getId());
        assertThat(summary.jobSummary()).isEqualTo(vacancy.content().jobSummary());
        assertThat(summary.publishedAt()).isEqualTo(NOW);
        assertThat(summary.acceptingApplications()).isTrue();
    }

    @Test
    void toSummary_withAnUnresolvableCompany_stillRendersTheCard() {
        JobSummaryResponse summary = mapper.toSummary(VacancyFixtures.published(TENANT), null, false);

        assertThat(summary.companyName()).isEmpty();
        assertThat(summary.companyLogoUrl()).isNull();
        assertThat(summary.acceptingApplications()).isFalse();
    }

    @Test
    void toSummary_exposesNothingInternal() {
        JsonNode body = json.valueToTree(mapper.toSummary(VacancyFixtures.published(TENANT), ACME, true));

        assertThat(fieldNames(body)).doesNotContain(
                "tenantId", "status", "version", "assignedRecruiterId", "hiringManagerId",
                "recruitmentPipelineId", "screeningQuestions", "createdAt", "updatedAt");
    }

    @Test
    void toDetail_serializesAsOneFlatObject_cardFieldsAndAdvertFieldsSideBySide() {
        JobVacancy vacancy = VacancyFixtures.published(TENANT);

        JobDetailResponse detail = mapper.toDetail(vacancy, ACME, true);
        JsonNode body = json.valueToTree(detail);

        // Card fields are unwrapped to the top level — there is no "summary" object.
        assertThat(fieldNames(body)).doesNotContain("summary");
        assertThat(body.get("title").asText()).isEqualTo("Senior Backend Engineer");
        assertThat(body.get("companyName").asText()).isEqualTo("Acme Corp");
        assertThat(body.get("slug").asText()).isEqualTo(detail.summary().slug());
        assertThat(body.get("acceptingApplications").asBoolean()).isTrue();

        // …alongside the fields only the detail page has.
        assertThat(body.get("jobDescription").asText()).startsWith("You will own the matching service.");
        assertThat(body.get("keyResponsibilities")).hasSize(1);
        assertThat(body.get("benefits").get(0).asText()).isEqualTo("HEALTH_INSURANCE");
        assertThat(body.get("workingDays").get(0).asText()).isEqualTo("MON");
        assertThat(body.get("shiftType").asText()).isEqualTo("DAY");
    }

    @Test
    void toDetail_exposesNothingInternal() {
        JsonNode body = json.valueToTree(mapper.toDetail(VacancyFixtures.published(TENANT), ACME, true));

        assertThat(fieldNames(body)).doesNotContain(
                "tenantId", "status", "version", "assignedRecruiterId", "hiringManagerId",
                "recruitmentPipelineId", "screeningQuestions");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
