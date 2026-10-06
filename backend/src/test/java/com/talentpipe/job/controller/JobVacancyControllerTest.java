package com.talentpipe.job.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.GlobalExceptionHandler;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(JobVacancyController.class)
@Import({JobTestSecurityConfig.class, GlobalExceptionHandler.class})
class JobVacancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobVacancyService service;

    private UUID tenantId;
    private UUID vacancyId;
    private JobVacancyResponse dummyResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        vacancyId = UUID.randomUUID();
        dummyResponse = buildResponse(vacancyId, VacancyStatus.DRAFT);
    }

    // ---------------------------------------------------------------- GET /jobs

    @Test
    void list_asCompanyAdmin_returns200AndPageResponseEnvelope() throws Exception {
        Page<JobVacancyResponse> page = new PageImpl<>(List.of(dummyResponse));
        when(service.list(tenantId, 0, 100)).thenReturn(page);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "0")
                        .param("size", "100")
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(vacancyId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void list_asHrManager_returns200() throws Exception {
        Page<JobVacancyResponse> page = new PageImpl<>(List.of(dummyResponse));
        when(service.list(tenantId, 0, 100)).thenReturn(page);

        mockMvc.perform(get("/api/v1/jobs")
                        .with(asHrManager(tenantId)))
                .andExpect(status().isOk());
    }

    @Test
    void list_asInterviewer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .with(asInterviewer(tenantId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_asCandidate_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .with(asCandidate(tenantId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- GET /jobs/{id}

    @Test
    void get_existing_returns200() throws Exception {
        when(service.get(vacancyId, tenantId)).thenReturn(dummyResponse);

        mockMvc.perform(get("/api/v1/jobs/{id}", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vacancyId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.get(vacancyId, tenantId))
                .thenThrow(new ResourceNotFoundException("Job vacancy not found"));

        mockMvc.perform(get("/api/v1/jobs/{id}", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Job vacancy not found"));
    }

    // ---------------------------------------------------------------- POST /jobs

    @Test
    void create_validDraft_returns201WithLocationHeader() throws Exception {
        JobVacancyRequest request = createDraftRequest();
        when(service.create(any(JobVacancyRequest.class), eq(tenantId))).thenReturn(dummyResponse);

        mockMvc.perform(post("/api/v1/jobs")
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/jobs/" + vacancyId))
                .andExpect(jsonPath("$.id").value(vacancyId.toString()));
    }

    @Test
    void create_invalidShape_returns400() throws Exception {
        JobVacancyRequest request = createDraftRequest();
        when(service.create(any(JobVacancyRequest.class), eq(tenantId)))
                .thenThrow(new InvalidRequestException("Job title must be 120 characters or fewer."));

        mockMvc.perform(post("/api/v1/jobs")
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Job title must be 120 characters or fewer."));
    }

    @Test
    void create_publishIncomplete_returns422() throws Exception {
        JobVacancyRequest request = createDraftRequest();
        when(service.create(any(JobVacancyRequest.class), eq(tenantId)))
                .thenThrow(new BusinessRuleException("This vacancy can't be published yet: add a job summary."));

        mockMvc.perform(post("/api/v1/jobs")
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("This vacancy can't be published yet: add a job summary."));
    }

    // ---------------------------------------------------------------- PUT /jobs/{id}

    @Test
    void update_success_returns200() throws Exception {
        JobVacancyUpdateRequest updateRequest = createUpdateRequest(0);
        JobVacancyResponse updated = buildResponse(vacancyId, VacancyStatus.DRAFT);
        when(service.update(eq(vacancyId), any(JobVacancyUpdateRequest.class), eq(tenantId)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/jobs/{id}", vacancyId)
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vacancyId.toString()));
    }

    @Test
    void update_optimisticLockFailure_returns409() throws Exception {
        JobVacancyUpdateRequest updateRequest = createUpdateRequest(0);
        when(service.update(eq(vacancyId), any(JobVacancyUpdateRequest.class), eq(tenantId)))
                .thenThrow(new ObjectOptimisticLockingFailureException(JobVacancy.class, vacancyId));

        mockMvc.perform(put("/api/v1/jobs/{id}", vacancyId)
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("This vacancy was changed by someone else while you were editing. Reload to see the latest version."));
    }

    @Test
    void update_onClosed_returns422() throws Exception {
        JobVacancyUpdateRequest updateRequest = createUpdateRequest(0);
        when(service.update(eq(vacancyId), any(JobVacancyUpdateRequest.class), eq(tenantId)))
                .thenThrow(new BusinessRuleException("This vacancy can't be edited. It has been closed or archived."));

        mockMvc.perform(put("/api/v1/jobs/{id}", vacancyId)
                        .with(asCompanyAdmin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("This vacancy can't be edited. It has been closed or archived."));
    }

    // ---------------------------------------------------------------- lifecycle endpoints

    @Test
    void publish_fromDraft_returns200() throws Exception {
        JobVacancyResponse published = buildResponse(vacancyId, VacancyStatus.PUBLISHED);
        when(service.publish(vacancyId, tenantId)).thenReturn(published);

        mockMvc.perform(post("/api/v1/jobs/{id}/publish", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void close_fromPublished_returns200() throws Exception {
        JobVacancyResponse closed = buildResponse(vacancyId, VacancyStatus.CLOSED);
        when(service.close(vacancyId, tenantId)).thenReturn(closed);

        mockMvc.perform(post("/api/v1/jobs/{id}/close", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void archive_fromClosed_returns200() throws Exception {
        JobVacancyResponse archived = buildResponse(vacancyId, VacancyStatus.ARCHIVED);
        when(service.archive(vacancyId, tenantId)).thenReturn(archived);

        mockMvc.perform(post("/api/v1/jobs/{id}/archive", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void duplicate_returns201WithLocation() throws Exception {
        UUID newId = UUID.randomUUID();
        JobVacancyResponse duplicated = buildResponse(newId, VacancyStatus.DRAFT);
        when(service.duplicate(vacancyId, tenantId)).thenReturn(duplicated);

        mockMvc.perform(post("/api/v1/jobs/{id}/duplicate", vacancyId)
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/jobs/" + newId))
                .andExpect(jsonPath("$.id").value(newId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ---------------------------------------------------------------- helpers

    private static RequestPostProcessor asCompanyAdmin(UUID tenantId) {
        return asPrincipal(tenantId, "COMPANY_ADMIN");
    }

    private static RequestPostProcessor asHrManager(UUID tenantId) {
        return asPrincipal(tenantId, "HR_MANAGER");
    }

    private static RequestPostProcessor asInterviewer(UUID tenantId) {
        return asPrincipal(tenantId, "INTERVIEWER");
    }

    private static RequestPostProcessor asCandidate(UUID tenantId) {
        return asPrincipal(tenantId, "CANDIDATE");
    }

    private static RequestPostProcessor asPrincipal(UUID tenantId, String role) {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), tenantId, role, "user@test.io");
        return request -> {
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            request.setAttribute(
                    SecurityMockMvcRequestPostProcessors.class.getName() + ".authentication", auth);
            return SecurityMockMvcRequestPostProcessors.authentication(auth).postProcessRequest(request);
        };
    }

    private JobVacancyRequest createDraftRequest() {
        return new JobVacancyRequest(
                "Junior Dev", "Engineering", 1, null, null, "Colombo", null,
                "", "", List.of(), List.of(), List.of(), null, null, List.of(),
                List.of(), null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );
    }

    private JobVacancyUpdateRequest createUpdateRequest(int version) {
        return new JobVacancyUpdateRequest(
                "Updated Dev", "Product", 2, EmploymentType.CONTRACT, WorkplaceType.REMOTE,
                "Remote region", LocalDate.of(2026, 12, 31), "Updated summary", "Updated description",
                List.of("Resp 1"), List.of("Kotlin"), List.of(), 4, null, List.of(),
                List.of(), null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, List.of(), version
        );
    }

    private JobVacancyResponse buildResponse(UUID id, VacancyStatus status) {
        return new JobVacancyResponse(
                id, 0, status, "Backend Engineer", "Engineering", 1,
                EmploymentType.FULL_TIME, WorkplaceType.ON_SITE, "Colombo",
                LocalDate.of(2026, 12, 31), "Summary", "Description",
                List.of("Resp 1"), List.of("Java"), List.of(), 2, null,
                List.of(), List.of(), null, BigDecimal.valueOf(3000),
                BigDecimal.valueOf(5000), "USD", PayPeriod.MONTHLY,
                List.of(), List.of(WeekDay.MON, WeekDay.TUE), "9-5",
                null, 40.0, null, null, "STANDARD", List.of(),
                Instant.now(), Instant.now(), null, null, null, null
        );
    }
}
