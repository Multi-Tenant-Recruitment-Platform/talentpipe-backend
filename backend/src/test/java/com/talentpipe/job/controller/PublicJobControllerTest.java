package com.talentpipe.job.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobFilterOption;
import com.talentpipe.job.dto.JobFilterOptionsResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.service.PublicJobService;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link PublicJobController}: the HTTP
 * contract of the public job board — open to everyone, parameters passed
 * through, and a response that carries nothing internal.
 */
@WebMvcTest(PublicJobController.class)
@Import(WebMvcSecurityTestConfig.class)
class PublicJobControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final CompanySummaryResponse ACME =
            new CompanySummaryResponse(TENANT, "Acme Corp", "acme", "https://cdn.example.com/acme.png", null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicJobService publicJobService;

    private final JobVacancyMapper mapper = new JobVacancyMapper();
    private final JobVacancy vacancy = VacancyFixtures.published(TENANT);
    private final JobSummaryResponse card = mapper.toSummary(vacancy, ACME, true);

    // -------------------------------------------------------------------- list

    @Test
    void list_needsNoAuthentication_andReturnsTheBoardCards() throws Exception {
        when(publicJobService.search(null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(card), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/public/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.content[0].companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.content[0].jobSummary").value(vacancy.content().jobSummary()))
                .andExpect(jsonPath("$.content[0].slug").value(card.slug()))
                .andExpect(jsonPath("$.content[0].acceptingApplications").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_passesKeywordCategoryLocationAndPagingToTheService() throws Exception {
        when(publicJobService.search("java", "Engineering", "Colombo", 2, 10))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/public/jobs")
                        .param("q", "java")
                        .param("category", "Engineering")
                        .param("location", "Colombo")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(publicJobService).search("java", "Engineering", "Colombo", 2, 10);
    }

    @Test
    void list_neverExposesInternalFields() throws Exception {
        when(publicJobService.search(null, null, null, 0, 20)).thenReturn(new PageImpl<>(List.of(card)));

        mockMvc.perform(get("/api/v1/public/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.content[0].assignedRecruiterId").doesNotExist())
                .andExpect(jsonPath("$.content[0].hiringManagerId").doesNotExist())
                .andExpect(jsonPath("$.content[0].screeningQuestions").doesNotExist());
    }

    @Test
    void list_isTheSameForASignedInUser_theBoardIsNotTenantScoped() throws Exception {
        when(publicJobService.search(null, null, null, 0, 20)).thenReturn(new PageImpl<>(List.of(card)));

        mockMvc.perform(get("/api/v1/public/jobs").with(asUser(UUID.randomUUID(), UUID.randomUUID(), "HR_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Acme Corp"));
    }

    @Test
    void list_withANonNumericSize_isABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/public/jobs").param("size", "lots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'size'"));
    }

    // ----------------------------------------------------------------- filters

    @Test
    void filters_returnsCategoriesAndLocationsWithCounts() throws Exception {
        when(publicJobService.filterOptions()).thenReturn(new JobFilterOptionsResponse(
                List.of(new JobFilterOption("Engineering", 7), new JobFilterOption("Design", 2)),
                List.of(new JobFilterOption("Colombo, Sri Lanka", 5))));

        mockMvc.perform(get("/api/v1/public/jobs/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].value").value("Engineering"))
                .andExpect(jsonPath("$.categories[0].count").value(7))
                .andExpect(jsonPath("$.categories[1].value").value("Design"))
                .andExpect(jsonPath("$.locations[0].value").value("Colombo, Sri Lanka"))
                .andExpect(jsonPath("$.locations[0].count").value(5));
    }

    // ------------------------------------------------------------------ detail

    @Test
    void detail_returnsOneFlatAdvert_cardFieldsAndBodyTogether() throws Exception {
        JobDetailResponse detail = mapper.toDetail(vacancy, ACME, true);
        when(publicJobService.getPublished(card.slug())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/public/jobs/{slug}", card.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.id").value(vacancy.getId().toString()))
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.jobDescription").value(vacancy.content().jobDescription()))
                .andExpect(jsonPath("$.keyResponsibilities[0]").value("Own the matching service"))
                .andExpect(jsonPath("$.workingDays[0]").value("MON"))
                .andExpect(jsonPath("$.screeningQuestions").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void detail_ofAnUnknownOrUnpublishedVacancy_answers404() throws Exception {
        when(publicJobService.getPublished("no-such-job")).thenThrow(new ResourceNotFoundException("Job not found"));

        mockMvc.perform(get("/api/v1/public/jobs/no-such-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Job not found"));
    }

    @Test
    void theWordFilters_isRoutedToTheFilterEndpoint_notTreatedAsASlug() throws Exception {
        when(publicJobService.filterOptions()).thenReturn(new JobFilterOptionsResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/v1/public/jobs/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray());

        verify(publicJobService).filterOptions();
    }
}
