package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.talentpipe.application.service.ApplicationScreeningService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * The recruiter's applicant dashboard (V29): screening on submit, the ranked
 * list, its counts, search, blind mode, tenant isolation and access scope;
 * the applicants area's vacancy overview; and one application in full, with
 * its CV. Screens with the stub scorer, which needs no AI service.
 */
class ApplicantDashboardIntegrationTest extends AbstractJobIntegrationTest {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void screenWithTheStub(DynamicPropertyRegistry registry) {
        registry.add("talentpipe.ai.screening-mode", () -> "stub");
        registry.add("talentpipe.storage.upload-dir", () -> uploads.toString());
    }

    @Autowired
    private ApplicationScreeningService screening;

    private Workspace company;
    private Object vacancyId;

    @BeforeEach
    void aPublishedVacancyThatScreens() {
        company = newWorkspace("Lumora Labs");
        vacancyId = createPublished(company.adminToken(), "Backend Engineer").get("id");
        jdbc.update("UPDATE job_vacancies SET ai_screening_enabled = true WHERE id = ?::uuid", vacancyId);
    }

    // -------------------------------------------------------------- screening

    @Test
    void aNewApplication_isScreened_movedOnByItsVerdict_andItsHistoryRecorded() {
        Object applicationId = apply(newCandidate("Nimal Perera"));

        Map<String, Object> row = onlyRow(list(company.adminToken(), ""));

        assertThat(row.get("applicationId")).isEqualTo(applicationId);
        assertThat(row.get("candidateName")).isEqualTo("Nimal Perera");
        assertThat(row.get("scoreStatus")).isEqualTo("SCORED");
        int score = (Integer) row.get("score");
        String expected = score >= 80 ? "AUTO_ADVANCED" : score >= 70 ? "MANUAL_REVIEW" : "REJECTED";
        assertThat(row.get("reviewStatus")).isEqualTo(expected);
        // Auto-advanced applicants go straight on to interview; the rest stay in screening.
        assertThat(((Map<?, ?>) row.get("currentStage")).get("type"))
                .isEqualTo(score >= 80 ? "INTERVIEW" : "SCREENING");

        assertThat(jdbc.queryForList("SELECT event_type FROM application_stage_events "
                + "WHERE application_id = ?::uuid ORDER BY created_at, id", String.class, applicationId))
                .containsExactly("SUBMITTED", "AI_SCREENED");
        assertThat(jdbc.queryForMap("SELECT outcome, auto_threshold_used, manual_threshold_used "
                + "FROM application_scores WHERE application_id = ?::uuid", applicationId))
                .containsEntry("auto_threshold_used", 80)
                .containsEntry("manual_threshold_used", 70);
    }

    @Test
    void aVacancyThatDoesNotScreen_sendsItsApplicants_straightToManualReview() {
        jdbc.update("UPDATE job_vacancies SET ai_screening_enabled = false WHERE id = ?::uuid", vacancyId);

        Object applicationId = apply(newCandidate("Kamal Silva"));
        awaitScreened(applicationId);

        Map<String, Object> row = onlyRow(list(company.adminToken(), ""));
        assertThat(row.get("reviewStatus")).isEqualTo("MANUAL_REVIEW");
        assertThat(row.get("scoreStatus")).isEqualTo("PENDING");
        assertThat(row.get("score")).isNull();
        assertThat(jdbc.queryForList("SELECT event_type FROM application_stage_events "
                + "WHERE application_id = ?::uuid ORDER BY created_at, id", String.class, applicationId))
                .containsExactly("SUBMITTED", "SCREENING_SKIPPED");
    }

    // ---------------------------------------------------------------- ranking

    @Test
    void theList_isRankedByGroup_thenByScore_andTheCountsAddUp() {
        Object low = setReview(apply(newCandidate("Ruwan Low")), "REJECTED", 40);
        Object manual = setReview(apply(newCandidate("Mala Manual")), "MANUAL_REVIEW", 75);
        Object auto85 = setReview(apply(newCandidate("Amal Eighty")), "AUTO_ADVANCED", 85);
        Object byRecruiter = setReview(apply(newCandidate("Rani Recruiter")), "ADVANCED_BY_RECRUITER", 60);
        Object auto92 = setReview(apply(newCandidate("Bimal Ninety")), "AUTO_ADVANCED", 92);

        Map<String, Object> page = list(company.adminToken(), "");

        assertThat(ids(page)).containsExactly(auto92, auto85, byRecruiter, manual, low);
        assertThat(counts(page))
                .containsEntry("all", 5)
                .containsEntry("autoAdvanced", 3)
                .containsEntry("advancedByRecruiter", 1)
                .containsEntry("manualReview", 1)
                .containsEntry("rejected", 1)
                .containsEntry("pending", 0);
        assertThat(ids(list(company.adminToken(), "?band=auto"))).containsExactly(auto92, auto85, byRecruiter);
        assertThat(ids(list(company.adminToken(), "?band=manual"))).containsExactly(manual);
        assertThat(ids(list(company.adminToken(), "?band=rejected"))).containsExactly(low);

        // Paging keeps the order: the second page of two starts where the first ended.
        assertThat(ids(list(company.adminToken(), "?size=2&page=1"))).containsExactly(byRecruiter, manual);
    }

    @Test
    void search_findsByNameOrEmail_andTreatsWildcardsAsText() {
        apply(newCandidate("Nimal Perera"));
        apply(newCandidate("Kamal Silva"));

        assertThat(rows(list(company.adminToken(), "?q=perera"))).hasSize(1);
        assertThat(rows(list(company.adminToken(), "?q=" + "%25"))).isEmpty();
        assertThat(rows(list(company.adminToken(), "?q=example.com"))).hasSize(2);
    }

    @Test
    void blindMode_hidesNamesAndEmails_andSearchCannotReachBehindIt() {
        jdbc.update("UPDATE job_vacancies SET blind_mode_enabled = true WHERE id = ?::uuid", vacancyId);
        apply(newCandidate("Nimal Perera"));

        // A search that would match nobody is ignored, so it cannot be used to test a guess.
        Map<String, Object> page = list(company.adminToken(), "?q=nobody&sort=name");

        assertThat(page.get("blindMode")).isEqualTo(true);
        Map<String, Object> row = onlyRow(page);
        assertThat(row.get("candidateName")).isEqualTo("Applicant #1");
        assertThat(row.get("email")).isNull();
    }

    // ---------------------------------------------------------------- polling

    @Test
    void theSummary_movesWhenSomeoneApplies() {
        Map<String, Object> before = summary(company.adminToken());
        assertThat(counts(before)).containsEntry("all", 0);
        assertThat(before.get("lastChangedAt")).isNull();

        awaitScreened(apply(newCandidate("Nimal Perera")));

        Map<String, Object> after = summary(company.adminToken());
        assertThat(counts(after)).containsEntry("all", 1);
        assertThat(after.get("lastChangedAt")).isNotNull();
    }

    // ----------------------------------------------------------------- access

    @Test
    void anotherCompany_cannotSeeTheApplicants_itGetsNotFound() {
        Workspace other = newWorkspace("Serendib Partners");

        assertThat(statusOf(HttpMethod.GET, applicantsPath(""), other.adminToken())).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, applicantsPath("/summary"), other.adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anInterviewerWhoDoesNotWorkOnTheVacancy_getsNotFound() {
        String interviewer = newMember(company, "INTERVIEWER");

        assertThat(statusOf(HttpMethod.GET, applicantsPath(""), interviewer)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnknownBand_isABadRequest() {
        assertThat(statusOf(HttpMethod.GET, applicantsPath("?band=shortlisted"), company.adminToken()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------- the older status change

    @Test
    void theOlderStatusChange_keepsItsResponse_andNowWritesTheDecisionIntoTheHistory() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        awaitScreened(applicationId);

        ResponseEntity<Map<String, Object>> response = call(HttpMethod.PATCH,
                "/api/v1/applications/" + applicationId + "/status", company.adminToken(),
                Map.of("status", "SHORTLISTED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "SHORTLISTED").containsKey("jobTitle");
        assertThat(onlyRow(list(company.adminToken(), "")).get("reviewStatus")).isEqualTo("ADVANCED_BY_RECRUITER");
        Map<String, Object> decision = jdbc.queryForMap("SELECT event_type, actor_user_id FROM application_stage_events "
                + "WHERE application_id = ?::uuid ORDER BY created_at DESC, id LIMIT 1", applicationId);
        assertThat(decision.get("event_type")).isEqualTo("SHORTLISTED");
        assertThat(decision.get("actor_user_id")).isNotNull();
    }

    // --------------------------------------------------------- vacancy overview

    @Test
    void theOverview_listsPublishedVacancies_withTheirCounts() {
        apply(newCandidate("Nimal Perera"));
        createPublished(company.adminToken(), "Nobody Applied Yet");

        List<Map<String, Object>> published = vacancies(company.adminToken(), "");

        assertThat(published).extracting(v -> v.get("title")).containsExactly("Backend Engineer", "Nobody Applied Yet");
        Map<String, Object> first = published.get(0);
        assertThat(first.get("status")).isEqualTo("PUBLISHED");
        assertThat(first.get("lastAppliedAt")).isNotNull();
        assertThat(counts(first)).containsEntry("all", 1);
        assertThat(counts(published.get(1))).containsEntry("all", 0);
    }

    @Test
    void theClosedTab_listsClosedVacancies_andArchivedOnesAreNeverListed() {
        Object closed = createPublished(company.adminToken(), "Closed Role").get("id");
        advance(company.adminToken(), closed, "close");
        Object archived = createPublished(company.adminToken(), "Archived Role").get("id");
        advance(company.adminToken(), archived, "close", "archive");

        assertThat(vacancies(company.adminToken(), "?status=closed")).extracting(v -> v.get("title"))
                .containsExactly("Closed Role");
        assertThat(vacancies(company.adminToken(), "")).extracting(v -> v.get("title"))
                .containsExactly("Backend Engineer");
    }

    @Test
    void theOverview_showsAnInterviewerOnlyWhatTheyWorkOn_andAnotherCompanyNothing() {
        assertThat(vacancies(newMember(company, "INTERVIEWER"), "")).isEmpty();
        assertThat(vacancies(newWorkspace("Serendib Partners").adminToken(), "")).isEmpty();
        assertThat(statusOf(HttpMethod.GET, "/api/v1/applicants/vacancies?status=archived", company.adminToken()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------- one application

    @Test
    void theReview_showsTheCandidate_theScoreAndWhy_theStages_andTheHistory() {
        Object applicationId = apply(newCandidate("Nimal Perera"));

        Map<String, Object> review = review(company.adminToken(), applicationId);

        Map<?, ?> candidate = (Map<?, ?>) review.get("candidate");
        assertThat(candidate.get("name")).isEqualTo("Nimal Perera");
        assertThat((String) candidate.get("email")).startsWith("nimal.perera-");
        assertThat(candidate.get("phone")).isEqualTo("+94 77 123 4567");

        Map<?, ?> score = (Map<?, ?>) review.get("score");
        assertThat(score.get("status")).isEqualTo("SCORED");
        assertThat(score.get("autoThresholdUsed")).isEqualTo(80);
        assertThat((List<?>) score.get("criteria")).isNotEmpty();

        assertThat(maps(review.get("stages")).stream().map(stage -> stage.get("type")).toList())
                .containsExactly("POST_APPLICATION", "SCREENING", "INTERVIEW", "ONBOARD");
        assertThat(review.get("currentStageId")).isNotNull();
        List<Map<String, Object>> history = maps(review.get("history"));
        assertThat(history.stream().map(line -> line.get("type")).toList())
                .containsExactly("SUBMITTED", "AI_SCREENED");
        assertThat(history.get(1).get("score")).isEqualTo(score.get("overallScore"));
        assertThat(review.get("cv")).isNull();
    }

    @Test
    void inBlindMode_theReviewHidesWhoTheCandidateIs() {
        jdbc.update("UPDATE job_vacancies SET blind_mode_enabled = true WHERE id = ?::uuid", vacancyId);
        Object applicationId = apply(newCandidate("Nimal Perera"));

        Map<?, ?> candidate = (Map<?, ?>) review(company.adminToken(), applicationId).get("candidate");

        assertThat(candidate.get("name")).isEqualTo("Applicant #1");
        assertThat(candidate.get("email")).isNull();
        assertThat(candidate.get("phone")).isNull();
        assertThat(candidate.get("location")).isNull();
    }

    @Test
    void anApplicationOutsideTheCallersScope_isNotFound() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        String path = "/api/v1/applications/" + applicationId + "/review";

        assertThat(statusOf(HttpMethod.GET, path, newWorkspace("Serendib Partners").adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, path, newMember(company, "INTERVIEWER"))).isEqualTo(HttpStatus.NOT_FOUND);
        // Applied without a CV: there is nothing to serve.
        assertThat(statusOf(HttpMethod.GET, "/api/v1/applications/" + applicationId + "/cv", company.adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theCv_isTheOneTheCandidateAppliedWith_evenAfterTheyReplaceIt() {
        String candidate = newCandidate("Nimal Perera");
        assertThat(uploadCv(candidate, "nimal-v1.pdf", "first").getStatusCode()).isEqualTo(HttpStatus.OK);
        Object applicationId = apply(candidate);
        // Removed first, as CvExtractionFlowIntegrationTest does: replacing an active CV in one
        // save currently fails on uq_candidate_active_cv (a separate, pre-existing issue).
        assertThat(removeCv(candidate).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploadCv(candidate, "nimal-v2.pdf", "second").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> cv = rest.exchange("/api/v1/applications/" + applicationId + "/cv?disposition=inline",
                HttpMethod.GET, new HttpEntity<>(bearer(company.adminToken())), byte[].class);

        assertThat(cv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(cv.getBody(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 first");
        assertThat(cv.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(cv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline").contains("nimal-v1.pdf");
        assertThat(cv.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(cv.getHeaders().getCacheControl()).contains("no-store");
        assertThat(((Map<?, ?>) review(company.adminToken(), applicationId).get("cv")).get("fileName"))
                .isEqualTo("nimal-v1.pdf");
    }

    // -------------------------------------------------- moving on to interview

    @Test
    void anAutoAdvancedApplicant_goesStraightToTheInterviewStage() {
        String candidate = newCandidate("Nimal Perera");
        assertThat(uploadCv(candidate, "nimal.pdf", "cv").getStatusCode()).isEqualTo(HttpStatus.OK);
        // The CV reads as covering every skill the vacancy asks for, so the stub scores it 100.
        jdbc.update("UPDATE candidate_cvs SET extracted_text = 'Java, Spring Boot and Kubernetes in production.', "
                + "extraction_status = 'EXTRACTED', text_extracted_at = now()");
        Object applicationId = apply(candidate);
        jdbc.update("UPDATE applications SET review_status = 'PENDING_SCREENING' WHERE id = ?::uuid", applicationId);
        jdbc.update("DELETE FROM application_scores WHERE application_id = ?::uuid", applicationId);

        screening.screen(UUID.fromString(applicationId.toString()));

        Map<String, Object> review = review(company.adminToken(), applicationId);
        assertThat(((Map<?, ?>) review.get("application")).get("reviewStatus")).isEqualTo("AUTO_ADVANCED");
        assertThat(((Map<?, ?>) review.get("score")).get("overallScore")).isEqualTo(100);
        assertThat(stageTypeOf(review)).isEqualTo("INTERVIEW");
    }

    @Test
    void aRecruiter_sendsAManualReviewApplicant_toInterview_andItIsRecorded() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        jdbc.update("UPDATE applications SET review_status = 'MANUAL_REVIEW' WHERE id = ?::uuid", applicationId);
        long version = ((Number) ((Map<?, ?>) review(company.adminToken(), applicationId).get("application"))
                .get("version")).longValue();

        ResponseEntity<Map<String, Object>> response = advance(company.adminToken(), applicationId, version);

        assertThat(response.getStatusCode()).as("advance: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        Map<?, ?> application = (Map<?, ?>) response.getBody().get("application");
        assertThat(application.get("reviewStatus")).isEqualTo("ADVANCED_BY_RECRUITER");
        assertThat(application.get("status")).isEqualTo("SHORTLISTED");
        assertThat(application.get("decidedAt")).isNotNull();
        assertThat(stageTypeOf(response.getBody())).isEqualTo("INTERVIEW");
        Map<String, Object> last = maps(response.getBody().get("history")).get(maps(response.getBody().get("history")).size() - 1);
        assertThat(last.get("type")).isEqualTo("ADVANCED");
        assertThat(last.get("toStage")).isEqualTo("Technical interview");
        assertThat(last.get("actorName")).isNotNull();
    }

    @Test
    void onlyManualReviewApplicants_canBeSentToInterview() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        for (String status : List.of("AUTO_ADVANCED", "REJECTED", "PENDING_SCREENING")) {
            jdbc.update("UPDATE applications SET review_status = ? WHERE id = ?::uuid", status, applicationId);
            long version = jdbc.queryForObject("SELECT version FROM applications WHERE id = ?::uuid", Long.class,
                    applicationId);

            assertThat(advance(company.adminToken(), applicationId, version).getStatusCode()).as(status)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Test
    void aDecisionOnAStaleCopy_isRefusedWithAConflict() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        jdbc.update("UPDATE applications SET review_status = 'MANUAL_REVIEW' WHERE id = ?::uuid", applicationId);
        long version = jdbc.queryForObject("SELECT version FROM applications WHERE id = ?::uuid", Long.class,
                applicationId);

        ResponseEntity<Map<String, Object>> response = advance(company.adminToken(), applicationId, version - 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "CONCURRENT_UPDATE");
    }

    @Test
    void onlyThoseWhoMayMoveApplicants_canSendThemToInterview() {
        Object applicationId = apply(newCandidate("Nimal Perera"));
        jdbc.update("UPDATE applications SET review_status = 'MANUAL_REVIEW' WHERE id = ?::uuid", applicationId);

        assertThat(advance(newMember(company, "INTERVIEWER"), applicationId, 0).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(advance(newWorkspace("Serendib Partners").adminToken(), applicationId, 0).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- helpers

    private String applicantsPath(String suffix) {
        return "/api/v1/jobs/" + vacancyId + "/applicants" + suffix;
    }

    private ResponseEntity<Map<String, Object>> advance(String token, Object applicationId, long version) {
        return call(HttpMethod.POST, "/api/v1/applications/" + applicationId + "/advance-to-interview", token,
                Map.of("version", version));
    }

    /** The type of the stage the review says the applicant is in. */
    private static Object stageTypeOf(Map<String, Object> review) {
        Object current = review.get("currentStageId");
        return maps(review.get("stages")).stream().filter(stage -> stage.get("id").equals(current))
                .map(stage -> stage.get("type")).findFirst().orElse(null);
    }

    private List<Map<String, Object>> vacancies(String token, String query) {
        ResponseEntity<List<Map<String, Object>>> response = callForList("/api/v1/applicants/vacancies" + query, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> review(String token, Object applicationId) {
        ResponseEntity<Map<String, Object>> response =
                call(HttpMethod.GET, "/api/v1/applications/" + applicationId + "/review", token, null);
        assertThat(response.getStatusCode()).as("review: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** Uploads a CV through the candidate's profile form. */
    private ResponseEntity<String> uploadCv(String candidateToken, String fileName, String content) {
        MultiValueMap<String, Object> form = profileForm(candidateToken);
        HttpHeaders pdf = new HttpHeaders();
        pdf.setContentType(MediaType.APPLICATION_PDF);
        byte[] bytes = ("%PDF-1.4 " + content).getBytes(StandardCharsets.UTF_8);
        form.add("cv", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }, pdf));
        return saveProfile(candidateToken, form);
    }

    /** Removes the candidate's active CV through the profile form. */
    private ResponseEntity<String> removeCv(String candidateToken) {
        MultiValueMap<String, Object> form = profileForm(candidateToken);
        form.add("removeCv", "true");
        return saveProfile(candidateToken, form);
    }

    private MultiValueMap<String, Object> profileForm(String candidateToken) {
        Map<String, Object> me = call(HttpMethod.GET, "/api/v1/candidates/me", candidateToken, null).getBody();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("fullName", me.get("fullName"));
        form.add("email", me.get("email"));
        form.add("identityCardNumber", "200012345678");
        form.add("phone", "+94 77 123 4567");
        form.add("addToTalentPool", "true");
        return form;
    }

    private ResponseEntity<String> saveProfile(String candidateToken, MultiValueMap<String, Object> form) {
        HttpHeaders headers = bearer(candidateToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/v1/candidates/me", HttpMethod.PUT, new HttpEntity<>(form, headers), String.class);
    }

    private Map<String, Object> list(String token, String query) {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.GET, applicantsPath(query), token, null);
        assertThat(response.getStatusCode()).as("list: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> summary(String token) {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.GET, applicantsPath("/summary"), token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private static Map<String, Object> onlyRow(Map<String, Object> page) {
        assertThat(rows(page)).hasSize(1);
        return rows(page).get(0);
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("applicationId")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> counts(Map<String, Object> page) {
        return (Map<String, Object>) page.get("counts");
    }

    /** Waits for the asynchronous screening, then pins the application's verdict so the test controls the order. */
    private Object setReview(Object applicationId, String reviewStatus, int score) {
        awaitScreened(applicationId);
        jdbc.update("UPDATE applications SET review_status = ? WHERE id = ?::uuid", reviewStatus, applicationId);
        jdbc.update("UPDATE application_scores SET overall_score = ? WHERE application_id = ?::uuid",
                score, applicationId);
        return applicationId;
    }

    private void awaitScreened(Object applicationId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> !"PENDING_SCREENING".equals(jdbc.queryForObject(
                "SELECT review_status FROM applications WHERE id = ?::uuid", String.class, applicationId)));
    }

    private Object apply(String candidateToken) {
        ResponseEntity<Map<String, Object>> response =
                call(HttpMethod.POST, "/api/v1/applications/jobs/" + vacancyId, candidateToken, null);
        assertThat(response.getStatusCode()).as("apply: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        Object id = response.getBody().get("id");
        awaitScreened(id);
        return id;
    }

    /** Registers a candidate, verifies their email and signs them in. */
    private String newCandidate(String fullName) {
        String email = fullName.toLowerCase().replace(' ', '.') + "-"
                + UUID.randomUUID().toString().substring(0, 6) + "@example.com";
        call(HttpMethod.POST, "/api/v1/public/candidates/register", null, Map.of(
                "fullName", fullName,
                "identityCardNumber", "200012345678",
                "address", "Colombo",
                "contactNumber", "+94 77 123 4567",
                "email", email,
                "password", PASSWORD));
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();
        ResponseEntity<Map<String, Object>> login = loginResponse(null, email, PASSWORD);
        assertThat(login.getStatusCode()).as("login: %s", login.getBody()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }
}
