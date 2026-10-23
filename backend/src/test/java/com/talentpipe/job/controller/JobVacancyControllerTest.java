package com.talentpipe.job.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.HiringTeamRequest;
import com.talentpipe.job.dto.HiringTeamResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.VacancyFeaturesRequest;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.job.service.VacancyHiringTeamService;
import com.talentpipe.job.service.VacancyListFilter;
import com.talentpipe.job.service.VacancyViewer;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code @WebMvcTest} slice tests for {@link JobVacancyController}: who may
 * call it, how requests are validated, that the tenant and actor come from the
 * token, and how each failure is reported. The service is mocked; its own
 * behavior is covered in {@code JobVacancyServiceTest}.
 */
@WebMvcTest(JobVacancyController.class)
@Import(WebMvcSecurityTestConfig.class)
class JobVacancyControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID VACANCY = UUID.randomUUID();

    /** The caller as an Owner or Recruiter sees them: every vacancy in the company. */
    private static final VacancyViewer SEES_ALL = new VacancyViewer(TENANT, USER, true);

    /** The caller as an Interviewer sees them: only the vacancies they work on. */
    private static final VacancyViewer WORKS_ON_IT = new VacancyViewer(TENANT, USER, false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobVacancyService vacancyService;

    @MockitoBean
    private VacancyHiringTeamService hiringTeamService;

    private final JobVacancyResponse draft =
            new JobVacancyMapper().toResponse(VacancyFixtures.draft(TENANT));

    // ----------------------------------------------------------- authorization

    @ParameterizedTest
    @ValueSource(strings = {"COMPANY_ADMIN", "HR_MANAGER"})
    void managersOfVacancies_mayUseTheApi_andSeeEveryVacancy(String role) throws Exception {
        when(vacancyService.get(SEES_ALL, VACANCY)).thenReturn(draft);

        mockMvc.perform(get("/api/v1/jobs/{id}", VACANCY).with(as(role)))
                .andExpect(status().isOk());
    }

    @Test
    void anInterviewer_readsOnlyWhatTheyWorkOn() throws Exception {
        when(vacancyService.get(WORKS_ON_IT, VACANCY)).thenReturn(draft);
        when(vacancyService.list(WORKS_ON_IT, VacancyListFilter.none(), 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/jobs/{id}", VACANCY).with(as("INTERVIEWER"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs").with(as("INTERVIEWER"))).andExpect(status().isOk());

        verify(vacancyService).list(WORKS_ON_IT, VacancyListFilter.none(), 0, 20);
    }

    @Test
    void anInterviewer_cannotChangeVacancies() throws Exception {
        mockMvc.perform(json(post("/api/v1/jobs"), draftBody()).with(as("INTERVIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/v1/jobs/{id}", VACANCY), draftBody()).with(as("INTERVIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs/{id}/publish", VACANCY).with(as("INTERVIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/v1/jobs/{id}/hiring-team", VACANCY), Map.of("memberIds", List.of()))
                        .with(as("INTERVIEWER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vacancyService, hiringTeamService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANDIDATE", "SUPER_ADMIN"})
    void anyoneOutsideACompany_isForbidden_onReadsAndWrites(String role) throws Exception {
        mockMvc.perform(get("/api/v1/jobs").with(as(role))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/{id}", VACANCY).with(as(role))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/jobs/{id}/hiring-team", VACANCY).with(as(role))).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/v1/jobs"), draftBody()).with(as(role))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs/{id}/publish", VACANCY).with(as(role))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/jobs/{id}/archive", VACANCY).with(as(role))).andExpect(status().isForbidden());

        verifyNoInteractions(vacancyService, hiringTeamService);
    }

    @Test
    void withoutAToken_everyEndpointAnswers401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs/counts")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/jobs"), draftBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/jobs/{id}/close", VACANCY)).andExpect(status().isUnauthorized());

        verifyNoInteractions(vacancyService);
    }

    // -------------------------------------------------------------------- list

    @Test
    void list_returnsThePageEnvelope_scopedToTheTokensTenant() throws Exception {
        when(vacancyService.list(SEES_ALL, VacancyListFilter.none(), 0, 100)).thenReturn(new PageImpl<>(List.of(draft)));

        mockMvc.perform(get("/api/v1/jobs").param("page", "0").param("size", "100").with(as("HR_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(draft.id().toString()))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                // The tenant never appears in a response.
                .andExpect(jsonPath("$.content[0].tenantId").doesNotExist());
    }

    @Test
    void list_passesEveryFilterThrough() throws Exception {
        VacancyListFilter filter = new VacancyListFilter("ACTIVE", "me", "Engineering");
        when(vacancyService.list(SEES_ALL, filter, 0, 20)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/jobs")
                        .param("status", "ACTIVE")
                        .param("assignedTo", "me")
                        .param("department", "Engineering")
                        .with(as("COMPANY_ADMIN")))
                .andExpect(status().isOk());

        verify(vacancyService).list(SEES_ALL, filter, 0, 20);
    }

    @Test
    void list_withANonNumericPage_isABadRequest_notAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("page", "abc").with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'page'"));
    }

    @Test
    void counts_returnsOneTotalPerStatus() throws Exception {
        Map<VacancyStatus, Long> counts = new EnumMap<>(VacancyStatus.class);
        counts.put(VacancyStatus.DRAFT, 3L);
        counts.put(VacancyStatus.PUBLISHED, 12L);
        counts.put(VacancyStatus.CLOSED, 0L);
        counts.put(VacancyStatus.ARCHIVED, 4L);
        when(vacancyService.countByStatus(SEES_ALL)).thenReturn(counts);

        mockMvc.perform(get("/api/v1/jobs/counts").with(as("HR_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DRAFT").value(3))
                .andExpect(jsonPath("$.PUBLISHED").value(12))
                .andExpect(jsonPath("$.CLOSED").value(0))
                .andExpect(jsonPath("$.ARCHIVED").value(4));
    }

    // --------------------------------------------------------------------- get

    @Test
    void get_unknownOrForeignVacancy_answers404WithTheErrorEnvelope() throws Exception {
        when(vacancyService.get(SEES_ALL, VACANCY)).thenThrow(new ResourceNotFoundException("Vacancy not found"));

        mockMvc.perform(get("/api/v1/jobs/{id}", VACANCY).with(as("HR_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vacancy not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/jobs/" + VACANCY));
    }

    @Test
    void get_withAMalformedId_isABadRequest_notAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/not-a-uuid").with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_answers201_andTakesTenantAndActorFromTheToken() throws Exception {
        when(vacancyService.create(eq(SEES_ALL), any(JobVacancyRequest.class))).thenReturn(draft);

        mockMvc.perform(json(post("/api/v1/jobs"), draftBody()).with(as("HR_MANAGER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.applicantCount").isEmpty());

        verify(vacancyService).create(eq(SEES_ALL), any(JobVacancyRequest.class));
    }

    @Test
    void create_acceptsAnAlmostEmptyDraft_nothingButOpeningsAndStatus() throws Exception {
        when(vacancyService.create(eq(SEES_ALL), any(JobVacancyRequest.class))).thenReturn(draft);

        mockMvc.perform(json(post("/api/v1/jobs"), Map.of("openings", 1, "status", "DRAFT"))
                        .with(as("HR_MANAGER")))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withoutAStatus_isABadRequest() throws Exception {
        Map<String, Object> body = draftBody();
        body.remove("status");

        mockMvc.perform(json(post("/api/v1/jobs"), body).with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(
                        "Choose whether to save this vacancy as a draft or publish it.")));

        verifyNoInteractions(vacancyService);
    }

    @Test
    void create_doesNotRequireAVersion_thatIsOnlyForEdits() throws Exception {
        when(vacancyService.create(eq(SEES_ALL), any(JobVacancyRequest.class))).thenReturn(draft);
        Map<String, Object> body = draftBody();
        body.remove("version");

        mockMvc.perform(json(post("/api/v1/jobs"), body).with(as("HR_MANAGER")))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withAnUnknownEnumValue_isABadRequest() throws Exception {
        Map<String, Object> body = draftBody();
        body.put("employmentType", "ZERO_HOURS");

        mockMvc.perform(json(post("/api/v1/jobs"), body).with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void create_withShapeViolations_isABadRequestNamingEachProblemInWords() throws Exception {
        Map<String, Object> body = draftBody();
        body.put("title", "T".repeat(121));
        body.put("openings", 0);
        body.put("salaryMin", -1);
        body.put("expectedHoursPerWeek", 200);
        body.put("screeningQuestions", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));

        mockMvc.perform(json(post("/api/v1/jobs"), body).with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Keep the job title to 120 characters or fewer.")))
                .andExpect(jsonPath("$.message", containsString("Enter at least one opening.")))
                .andExpect(jsonPath("$.message", containsString("A salary can't be negative.")))
                .andExpect(jsonPath("$.message", containsString("There are only 168 hours in a week.")))
                .andExpect(jsonPath("$.message", containsString("Ask at most 10 screening questions")));

        verifyNoInteractions(vacancyService);
    }

    @Test
    void create_refusedByABusinessRule_answers422WithTheRecruitersSentence() throws Exception {
        String sentence = "This vacancy can't be published yet: add a job summary.";
        when(vacancyService.create(eq(SEES_ALL), any(JobVacancyRequest.class)))
                .thenThrow(new BusinessRuleException(sentence));

        mockMvc.perform(json(post("/api/v1/jobs"), draftBody()).with(as("HR_MANAGER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(sentence));
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_requiresTheVersionBeingEdited() throws Exception {
        Map<String, Object> body = draftBody();
        body.remove("version");

        mockMvc.perform(json(put("/api/v1/jobs/{id}", VACANCY), body).with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("version")));

        verifyNoInteractions(vacancyService);
    }

    @Test
    void update_doesNotRequireAStatus_statusIsNotPartOfAnEdit() throws Exception {
        when(vacancyService.update(eq(SEES_ALL), eq(VACANCY), any(JobVacancyRequest.class)))
                .thenReturn(draft);
        Map<String, Object> body = draftBody();
        body.remove("status");

        mockMvc.perform(json(put("/api/v1/jobs/{id}", VACANCY), body).with(as("COMPANY_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draft.id().toString()));
    }

    @Test
    void update_withAStaleVersion_answers409() throws Exception {
        when(vacancyService.update(eq(SEES_ALL), eq(VACANCY), any(JobVacancyRequest.class)))
                .thenThrow(new ConcurrentUpdateException("Someone else saved changes to this vacancy."));

        mockMvc.perform(json(put("/api/v1/jobs/{id}", VACANCY), draftBody()).with(as("HR_MANAGER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Someone else saved changes to this vacancy."));
    }

    @Test
    void update_losingARaceAtTheDatabase_alsoAnswers409_notA500() throws Exception {
        when(vacancyService.update(eq(SEES_ALL), eq(VACANCY), any(JobVacancyRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("JobVacancy", VACANCY));

        mockMvc.perform(json(put("/api/v1/jobs/{id}", VACANCY), draftBody()).with(as("HR_MANAGER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("This record was changed by someone else. Reload it and try again."));
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    void publish_close_archive_answer200WithTheStoredVacancy() throws Exception {
        when(vacancyService.publish(SEES_ALL, VACANCY)).thenReturn(draft);
        when(vacancyService.close(SEES_ALL, VACANCY)).thenReturn(draft);
        when(vacancyService.archive(SEES_ALL, VACANCY)).thenReturn(draft);

        for (String move : List.of("publish", "close", "archive")) {
            mockMvc.perform(post("/api/v1/jobs/{id}/" + move, VACANCY).with(as("HR_MANAGER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(draft.id().toString()));
        }

        verify(vacancyService).publish(SEES_ALL, VACANCY);
        verify(vacancyService).close(SEES_ALL, VACANCY);
        verify(vacancyService).archive(SEES_ALL, VACANCY);
    }

    @Test
    void anIllegalMove_answers422WithTheRuleThatWasBroken() throws Exception {
        String sentence = "Only a published vacancy can be closed. This vacancy is a draft.";
        when(vacancyService.close(SEES_ALL, VACANCY)).thenThrow(new BusinessRuleException(sentence));

        mockMvc.perform(post("/api/v1/jobs/{id}/close", VACANCY).with(as("HR_MANAGER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(sentence));
    }

    @Test
    void duplicate_answers201_becauseANewVacancyNowExists() throws Exception {
        when(vacancyService.duplicate(SEES_ALL, VACANCY)).thenReturn(draft);

        mockMvc.perform(post("/api/v1/jobs/{id}/duplicate", VACANCY).with(as("HR_MANAGER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // -------------------------------------------------------- feature switches

    @Test
    void features_areSwitchedByAManagerOfVacancies() throws Exception {
        VacancyFeaturesRequest request = new VacancyFeaturesRequest(true, null);
        when(vacancyService.switchFeatures(SEES_ALL, VACANCY, request)).thenReturn(draft);

        mockMvc.perform(json(patch("/api/v1/jobs/{id}/features", VACANCY), Map.of("aiScreeningEnabled", true))
                        .with(as("HR_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiScreeningEnabled").isBoolean())
                .andExpect(jsonPath("$.candidateMatchingEnabled").isBoolean());

        verify(vacancyService).switchFeatures(SEES_ALL, VACANCY, request);
    }

    @Test
    void features_cannotBeSwitchedByAnInterviewer() throws Exception {
        mockMvc.perform(json(patch("/api/v1/jobs/{id}/features", VACANCY), Map.of("aiScreeningEnabled", true))
                        .with(as("INTERVIEWER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vacancyService);
    }

    // ------------------------------------------------------------ hiring team

    @Test
    void hiringTeam_isReadableByAnyoneWhoWorksOnTheVacancy() throws Exception {
        when(hiringTeamService.get(WORKS_ON_IT, VACANCY))
                .thenReturn(new HiringTeamResponse(VACANCY, null, null, List.of()));

        mockMvc.perform(get("/api/v1/jobs/{id}/hiring-team", VACANCY).with(as("INTERVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancyId").value(VACANCY.toString()))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    void hiringTeam_isReplacedWholesale_byAManagerOfVacancies() throws Exception {
        UUID member = UUID.randomUUID();
        HiringTeamRequest request = new HiringTeamRequest(List.of(member));
        when(hiringTeamService.replace(SEES_ALL, VACANCY, request))
                .thenReturn(new HiringTeamResponse(VACANCY, null, null, List.of()));

        mockMvc.perform(json(put("/api/v1/jobs/{id}/hiring-team", VACANCY), request).with(as("HR_MANAGER")))
                .andExpect(status().isOk());

        verify(hiringTeamService).replace(SEES_ALL, VACANCY, request);
    }

    @Test
    void hiringTeam_withoutAList_isRejectedFieldByField() throws Exception {
        mockMvc.perform(json(put("/api/v1/jobs/{id}/hiring-team", VACANCY), Map.of()).with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("memberIds"));

        verifyNoInteractions(hiringTeamService);
    }

    @Test
    void hiringTeam_largerThanTheLimit_isRejected() throws Exception {
        List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(HiringTeamRequest.MAX_MEMBERS + 1L)
                .toList();

        mockMvc.perform(json(put("/api/v1/jobs/{id}/hiring-team", VACANCY), Map.of("memberIds", tooMany))
                        .with(as("HR_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at most 50")));
    }

    // ----------------------------------------------------------------- helpers

    private static RequestPostProcessor as(String role) {
        return asUser(USER, TENANT, role);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    /** A complete vacancy body as the frontend sends it, as a mutable map so a test can break one field. */
    private Map<String, Object> draftBody() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, 0);
        return new HashMap<>(objectMapper.convertValue(request,
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)));
    }
}
