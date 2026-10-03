package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.job.repository.PublicJobSearchCriteria;
import com.talentpipe.job.repository.PublicJobSearchQuery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * End-to-end tests for the public job board (PB-017 / PB-018) against a real
 * PostgreSQL: what is and is not visible to candidates, keyword search,
 * category and location filters, and — because "fast" is part of the story —
 * that each query is actually served by the index built for it.
 *
 * <p>These need a real database by nature: full-text search, the generated
 * search column and trigram matching do not exist in an in-memory stand-in.</p>
 */
class PublicJobSearchIntegrationTest extends AbstractJobIntegrationTest {

    private static final String BOARD = "/api/v1/public/jobs";

    // --------------------------------------------------- portal synchronisation

    @Test
    void onlyPublishedVacancies_appearOnTheBoard_withTitleCompanyAndSummary() {
        Workspace acme = newWorkspace("Acme Robotics");
        String token = acme.adminToken();
        create(token, completeVacancy("Still a draft"));
        Object closed = createPublished(token, "Already closed").get("id");
        advance(token, closed, "close");
        Object archived = createPublished(token, "Long archived").get("id");
        advance(token, archived, "close", "archive");
        createPublished(token, "Open role");

        List<Map<String, Object>> cards = content(get(BOARD));

        assertThat(titles(cards)).containsExactly("Open role");
        Map<String, Object> card = cards.get(0);
        assertThat(card.get("companyName")).isEqualTo("Acme Robotics");
        assertThat(card.get("jobSummary")).isEqualTo("Lead the services behind our candidate pipeline.");
        assertThat(card.get("location")).isEqualTo("Colombo, Sri Lanka");
        assertThat(card.get("requiredSkills")).isEqualTo(List.of("Java", "Spring Boot"));
        assertThat(card.get("publishedAt")).isNotNull();
        assertThat(card.get("acceptingApplications")).isEqualTo(true);
        // Nothing internal reaches an anonymous visitor.
        assertThat(card).doesNotContainKeys(
                "tenantId", "status", "version", "assignedRecruiterId", "hiringManagerId", "screeningQuestions");
    }

    @Test
    void publishing_putsAVacancyOnTheBoardAtOnce_andClosingTakesItOffAtOnce() {
        Workspace acme = newWorkspace("Acme");
        Object id = create(acme.adminToken(), completeVacancy("Now you see me")).get("id");
        assertThat(content(get(BOARD))).isEmpty();

        advance(acme.adminToken(), id, "publish");
        assertThat(titles(content(get(BOARD)))).containsExactly("Now you see me");

        advance(acme.adminToken(), id, "close");
        assertThat(content(get(BOARD))).isEmpty();
        assertThat(get(BOARD).getBody().get("totalElements")).isEqualTo(0);
    }

    @Test
    void theBoardSpansCompanies_andASignedInRecruiterSeesTheSameBoardAsAnyoneElse() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        createPublished(acme.adminToken(), "Role at Acme");
        createPublished(globex.adminToken(), "Role at Globex");

        List<Map<String, Object>> anonymous = content(get(BOARD));
        List<Map<String, Object>> asAcmeAdmin = content(call(HttpMethod.GET, BOARD, acme.adminToken(), null));

        assertThat(anonymous).extracting(card -> card.get("title") + " @ " + card.get("companyName"))
                .containsExactly("Role at Globex @ Globex", "Role at Acme @ Acme");
        assertThat(titles(asAcmeAdmin)).isEqualTo(titles(anonymous));
    }

    @Test
    void anEditToALiveVacancy_isSearchableImmediately_andTheOldWordingNoLongerMatches() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Rust Engineer");
        body.put("status", "PUBLISHED");
        Map<String, Object> live = create(acme.adminToken(), body);
        assertThat(titles(search("rust", "", ""))).containsExactly("Rust Engineer");
        assertThat(search("golang", "", "")).isEmpty();

        body.put("title", "Golang Engineer");
        body.put("version", live.get("version"));
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + live.get("id"), acme.adminToken(), body).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // No reindex step, no delay: the search document is regenerated with the row.
        assertThat(titles(search("golang", "", ""))).containsExactly("Golang Engineer");
        assertThat(search("rust", "", "")).isEmpty();
    }

    @Test
    void aVacancyPastItsDeadline_staysListed_butIsMarkedAsNotAcceptingApplications() {
        Workspace acme = newWorkspace("Acme");
        Object id = createPublished(acme.adminToken(), "Deadline gone").get("id");
        jdbc.update("UPDATE job_vacancies SET application_deadline = current_date - 2 WHERE id = ?::uuid", id);

        List<Map<String, Object>> cards = content(get(BOARD));

        assertThat(titles(cards)).containsExactly("Deadline gone");
        assertThat(cards.get(0).get("acceptingApplications")).isEqualTo(false);
    }

    // ---------------------------------------------------------- keyword search

    @Test
    void keyword_matchesTitle_skills_andDescription() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();
        createPublished(token, "Embedded Firmware Engineer");

        Map<String, Object> bySkill = completeVacancy("Platform Developer");
        bySkill.put("requiredSkills", List.of("Terraform", "Ansible"));
        bySkill.put("status", "PUBLISHED");
        create(token, bySkill);

        Map<String, Object> byDescription = completeVacancy("Operations Lead");
        byDescription.put("jobDescription", "You will run our warehouse logistics across three sites.");
        byDescription.put("status", "PUBLISHED");
        create(token, byDescription);

        assertThat(titles(search("firmware", "", ""))).containsExactly("Embedded Firmware Engineer");
        assertThat(titles(search("terraform", "", ""))).containsExactly("Platform Developer");
        assertThat(titles(search("logistics", "", ""))).containsExactly("Operations Lead");
    }

    @Test
    void keyword_ranksATitleMatchAboveABodyMatch_evenWhenTheBodyMatchIsNewer() {
        Workspace acme = newWorkspace("Acme");
        createPublished(acme.adminToken(), "Python Developer");
        Map<String, Object> mentionsItInPassing = completeVacancy("Data Analyst");
        mentionsItInPassing.put("jobDescription", "Reporting in SQL, with the occasional Python script.");
        mentionsItInPassing.put("status", "PUBLISHED");
        create(acme.adminToken(), mentionsItInPassing);

        // Without a keyword the newer vacancy leads; with one, relevance does.
        assertThat(titles(content(get(BOARD)))).containsExactly("Data Analyst", "Python Developer");
        assertThat(titles(search("python", "", ""))).containsExactly("Python Developer", "Data Analyst");
    }

    @Test
    void keyword_isForgivingAboutWordFormsAndCase() {
        Workspace acme = newWorkspace("Acme");
        createPublished(acme.adminToken(), "Site Reliability Engineer");

        assertThat(search("engineers", "", "")).hasSize(1);
        assertThat(search("ENGINEERING", "", "")).hasSize(1);
        assertThat(search("reliable", "", "")).hasSize(1);
    }

    @Test
    void keyword_findsSkillsWrittenWithPunctuation_withOrWithoutIt() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Web Developer");
        body.put("requiredSkills", List.of("Node.js", "CI/CD"));
        body.put("status", "PUBLISHED");
        create(acme.adminToken(), body);

        assertThat(search("node", "", "")).as("node").hasSize(1);
        assertThat(search("node.js", "", "")).as("node.js").hasSize(1);
        assertThat(search("ci", "", "")).as("ci").hasSize(1);
    }

    @Test
    void keyword_understandsPhrasesExclusionsAndOr() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();
        create(token, published("Backend A", List.of("Java", "Spring Boot")));
        create(token, published("Backend B", List.of("Java", "Kotlin")));
        create(token, published("Frontend C", List.of("TypeScript")));

        assertThat(titles(search("\"spring boot\"", "", ""))).containsExactly("Backend A");
        assertThat(titles(search("java -kotlin", "", ""))).containsExactly("Backend A");
        assertThat(titles(search("kotlin OR typescript", "", "")))
                .containsExactlyInAnyOrder("Backend B", "Frontend C");
        // Several words mean all of them.
        assertThat(titles(search("java kotlin", "", ""))).containsExactly("Backend B");
    }

    @Test
    void keyword_thatMatchesNothing_returnsAnEmptyPage_notAnError() {
        Workspace acme = newWorkspace("Acme");
        createPublished(acme.adminToken(), "Accountant");

        ResponseEntity<Map<String, Object>> response = searchResponse("xylophonist", "", "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content(response)).isEmpty();
        assertThat(response.getBody().get("totalElements")).isEqualTo(0);
        assertThat(response.getBody().get("totalPages")).isEqualTo(0);
    }

    @Test
    void keyword_cannotBreakTheQuery_whateverIsTyped() {
        Workspace acme = newWorkspace("Acme");
        createPublished(acme.adminToken(), "Accountant");

        for (String hostile : List.of(
                "\"unbalanced quote",
                "((( & | ! :* <->",
                "'; DROP TABLE job_vacancies; --",
                "%_\\",
                // Long, but short enough that all three copies still fit in a request line.
                "a".repeat(400))) {
            assertThat(searchResponse(hostile, hostile, hostile).getStatusCode())
                    .as("input: %s", hostile.length() > 40 ? hostile.substring(0, 40) + "…" : hostile)
                    .isEqualTo(HttpStatus.OK);
        }
        // …and the table is, of course, still there.
        assertThat(titles(content(get(BOARD)))).containsExactly("Accountant");
    }

    // ------------------------------------------------------ category & location

    @Test
    void category_matchesTheWholeDepartment_ignoringCase() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        create(acme.adminToken(), published("Acme engineer", "Engineering", "Colombo, Sri Lanka"));
        create(globex.adminToken(), published("Globex engineer", "engineering", "Kandy, Sri Lanka"));
        create(acme.adminToken(), published("Acme marketer", "Marketing", "Colombo, Sri Lanka"));

        assertThat(titles(search("", "ENGINEERING", "")))
                .containsExactlyInAnyOrder("Acme engineer", "Globex engineer");
        assertThat(titles(search("", "Marketing", ""))).containsExactly("Acme marketer");
        // Whole value, not a prefix: a category is chosen from a list, not typed.
        assertThat(search("", "Engineer", "")).isEmpty();
    }

    @Test
    void location_matchesAnywhereInTheLocation_ignoringCase() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();
        create(token, published("In Colombo", "Engineering", "Colombo, Sri Lanka"));
        create(token, published("In Kandy", "Engineering", "Kandy, Sri Lanka"));
        create(token, published("Remote", "Engineering", "Remote (Sri Lanka)"));
        create(token, published("In Berlin", "Engineering", "Berlin, Germany"));

        assertThat(titles(search("", "", "COLOMBO"))).containsExactly("In Colombo");
        assertThat(titles(search("", "", "sri lanka")))
                .containsExactlyInAnyOrder("In Colombo", "In Kandy", "Remote");
        assertThat(titles(search("", "", "Remote (Sri"))).containsExactly("Remote");
        assertThat(search("", "", "Atlantis")).isEmpty();
    }

    @Test
    void location_treatsLikeWildcardsAsOrdinaryCharacters() {
        Workspace acme = newWorkspace("Acme");
        create(acme.adminToken(), published("Anywhere", "Engineering", "Colombo, Sri Lanka"));

        // "%" and "_" would match every row if they reached LIKE unescaped.
        assertThat(search("", "", "%")).isEmpty();
        assertThat(search("", "", "_")).isEmpty();
        assertThat(search("", "", "Col%bo")).isEmpty();
    }

    @Test
    void keywordCategoryAndLocation_combineWithAnd() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();
        create(token, published("Java Engineer", "Engineering", "Colombo, Sri Lanka"));
        create(token, published("Java Trainer", "Education", "Colombo, Sri Lanka"));
        create(token, published("Java Architect", "Engineering", "Berlin, Germany"));

        assertThat(titles(search("java", "", ""))).hasSize(3);
        assertThat(titles(search("java", "Engineering", "")))
                .containsExactlyInAnyOrder("Java Engineer", "Java Architect");
        assertThat(titles(search("java", "Engineering", "colombo"))).containsExactly("Java Engineer");
        assertThat(search("trainer", "Engineering", "colombo")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void filters_listCategoriesAndLocationsOfPublishedVacancies_withCounts() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        create(acme.adminToken(), published("A", "Engineering", "Colombo, Sri Lanka"));
        create(globex.adminToken(), published("B", "engineering", "Colombo, Sri Lanka"));
        create(acme.adminToken(), published("C", "Design", "Kandy, Sri Lanka"));
        // A draft must not create a filter option that returns nothing.
        Map<String, Object> draft = completeVacancy("D");
        draft.put("department", "Secret Projects");
        draft.put("location", "Area 51");
        create(acme.adminToken(), draft);

        Map<String, Object> filters = get(BOARD + "/filters").getBody();
        List<Map<String, Object>> categories = (List<Map<String, Object>>) filters.get("categories");
        List<Map<String, Object>> locations = (List<Map<String, Object>>) filters.get("locations");

        // Most populated first; "Engineering" and "engineering" are one option.
        assertThat(categories).hasSize(2);
        assertThat((String) categories.get(0).get("value")).isEqualToIgnoringCase("Engineering");
        assertThat(categories.get(0).get("count")).isEqualTo(2);
        assertThat(categories.get(1)).containsEntry("value", "Design").containsEntry("count", 1);
        assertThat(locations).extracting(option -> option.get("value") + " x" + option.get("count"))
                .containsExactly("Colombo, Sri Lanka x2", "Kandy, Sri Lanka x1");

        // Every offered value works when sent back as a filter.
        assertThat(search("", (String) categories.get(0).get("value"), "")).hasSize(2);
        assertThat(search("", "", (String) locations.get(1).get("value"))).hasSize(1);
    }

    // -------------------------------------------------------------- pagination

    @Test
    void theBoard_isNewestFirst_andPagesWithoutOverlap() {
        Workspace acme = newWorkspace("Acme");
        for (int i = 1; i <= 5; i++) {
            createPublished(acme.adminToken(), "Role " + i);
        }

        Map<String, Object> first = get(BOARD + "?page=0&size=2").getBody();
        Map<String, Object> second = get(BOARD + "?page=1&size=2").getBody();
        Map<String, Object> third = get(BOARD + "?page=2&size=2").getBody();

        assertThat(titles(content(first))).containsExactly("Role 5", "Role 4");
        assertThat(titles(content(second))).containsExactly("Role 3", "Role 2");
        assertThat(titles(content(third))).containsExactly("Role 1");
        assertThat(first.get("totalElements")).isEqualTo(5);
        assertThat(first.get("totalPages")).isEqualTo(3);
        assertThat(first.get("size")).isEqualTo(2);
    }

    @Test
    void pageSize_isCappedAtFifty_andNonsensePagingIsClampedNotRejected() {
        Workspace acme = newWorkspace("Acme");
        createPublished(acme.adminToken(), "Only role");

        assertThat(get(BOARD + "?size=5000").getBody().get("size")).isEqualTo(50);
        ResponseEntity<Map<String, Object>> clamped = get(BOARD + "?page=-3&size=0");
        assertThat(clamped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clamped.getBody().get("page")).isEqualTo(0);
        assertThat(clamped.getBody().get("size")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ detail

    @Test
    void detail_isFoundByTheCardsSlug_andByPlainId_andIsOneFlatAdvert() {
        Workspace acme = newWorkspace("Acme Robotics");
        Object id = createPublished(acme.adminToken(), "Senior Backend Engineer").get("id");
        String slug = (String) content(get(BOARD)).get(0).get("slug");

        assertThat(slug).startsWith("senior-backend-engineer-acme-robotics-");
        for (Object key : List.of(slug, id)) {
            ResponseEntity<Map<String, Object>> response = get(BOARD + "/" + key);
            assertThat(response.getStatusCode()).as("key %s", key).isEqualTo(HttpStatus.OK);
            Map<String, Object> advert = response.getBody();
            assertThat(advert.get("id")).isEqualTo(id);
            assertThat(advert.get("title")).isEqualTo("Senior Backend Engineer");
            assertThat(advert.get("companyName")).isEqualTo("Acme Robotics");
            assertThat((String) advert.get("jobDescription")).startsWith("You will own the matching service.");
            assertThat(advert.get("keyResponsibilities")).isEqualTo(List.of("Own the matching service"));
            assertThat(advert.get("benefits")).isEqualTo(List.of("HEALTH_INSURANCE"));
            assertThat(advert).doesNotContainKeys("summary", "tenantId", "status", "screeningQuestions");
        }
    }

    @Test
    void detail_linkSharedBeforeATitleEdit_stillResolves() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Old title");
        body.put("status", "PUBLISHED");
        Map<String, Object> live = create(acme.adminToken(), body);
        String oldSlug = (String) content(get(BOARD)).get(0).get("slug");

        body.put("title", "New title");
        body.put("version", live.get("version"));
        call(HttpMethod.PUT, "/api/v1/jobs/" + live.get("id"), acme.adminToken(), body);

        assertThat(get(BOARD + "/" + oldSlug).getBody().get("title")).isEqualTo("New title");
    }

    @Test
    void detail_ofADraftClosedOrUnknownVacancy_answers404_allAlike() {
        Workspace acme = newWorkspace("Acme");
        Object draft = create(acme.adminToken(), completeVacancy("Draft")).get("id");
        Object closed = createPublished(acme.adminToken(), "Closed").get("id");
        advance(acme.adminToken(), closed, "close");

        for (Object key : List.of(draft, closed, "00000000-0000-0000-0000-000000000000", "no-such-job")) {
            ResponseEntity<Map<String, Object>> response = get(BOARD + "/" + key);
            assertThat(response.getStatusCode()).as("key %s", key).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("message")).isEqualTo("Job not found");
        }
    }

    // ------------------------------------------------------------ index usage

    @Test
    void everySearchShape_isServedByTheIndexBuiltForIt() {
        Workspace acme = newWorkspace("Acme");
        // Enough rows that a sequential scan is no longer the cheapest plan,
        // spread thinly so each filter below is selective.
        jdbc.update("""
                INSERT INTO job_vacancies (tenant_id, status, title, department, location, job_summary,
                                           job_description, required_skills, application_deadline, published_at)
                SELECT ?, 'PUBLISHED', 'Filler role ' || g, 'Department ' || (g % 50), 'Town ' || (g % 80),
                       'Summary of filler role ' || g,
                       'A longer body of text describing what filler role number ' || g || ' involves day to day.',
                       ARRAY['skill' || g], current_date + 30, now() - make_interval(mins => g)
                FROM generate_series(1, 5000) AS g
                """, acme.tenantId());
        create(acme.adminToken(), published("Kotlin Developer", "Quantum Research", "Reykjavik, Iceland"));
        jdbc.execute("ANALYZE job_vacancies");

        PublicJobSearchCriteria browse = PublicJobSearchCriteria.none();
        PublicJobSearchCriteria byKeyword = PublicJobSearchCriteria.of("kotlin", null, null);
        PublicJobSearchCriteria byCategory = PublicJobSearchCriteria.of(null, "Quantum Research", null);
        PublicJobSearchCriteria byLocation = PublicJobSearchCriteria.of(null, null, "reykjavik");

        // Browsing, with or without a category: rows come straight off the
        // index already in display order — there is no sort step at all.
        assertThat(explainPage(browse)).contains("idx_job_vacancies_public_recent").doesNotContain("Sort");
        assertThat(explainPage(byCategory)).contains("idx_job_vacancies_public_category").doesNotContain("Sort");

        // Keyword and location: the matching rows are found through the GIN
        // indexes, never by reading every published advert.
        assertThat(explainPage(byKeyword)).contains("idx_job_vacancies_public_search").doesNotContain("Seq Scan");
        assertThat(explainPage(byLocation)).contains("idx_job_vacancies_public_location").doesNotContain("Seq Scan");

        // The count behind each page's totalElements takes the same route.
        assertThat(explainCount(byKeyword)).contains("idx_job_vacancies_public_search");
        assertThat(explainCount(byCategory)).contains("idx_job_vacancies_public_category");
        assertThat(explainCount(byLocation)).contains("idx_job_vacancies_public_location");

        // And the same statements, run for real, find the one row.
        assertThat(titles(search("kotlin", "", ""))).containsExactly("Kotlin Developer");
        assertThat(titles(search("", "quantum research", ""))).containsExactly("Kotlin Developer");
        assertThat(titles(search("", "", "reykjavik"))).containsExactly("Kotlin Developer");
        assertThat(get(BOARD).getBody().get("totalElements")).isEqualTo(5001);
    }

    // ----------------------------------------------------------------- helpers

    /** The plan PostgreSQL chooses for one page of the production select statement. */
    private String explainPage(PublicJobSearchCriteria criteria) {
        return explain(PublicJobSearchQuery.of(criteria).selectSql() + " LIMIT 20", criteria);
    }

    /** The plan PostgreSQL chooses for the production count statement of the given criteria. */
    private String explainCount(PublicJobSearchCriteria criteria) {
        return explain(PublicJobSearchQuery.of(criteria).countSql(), criteria);
    }

    private String explain(String sql, PublicJobSearchCriteria criteria) {
        List<String> plan = new NamedParameterJdbcTemplate(jdbc).queryForList(
                "EXPLAIN " + sql, PublicJobSearchQuery.of(criteria).parameters(), String.class);
        return String.join("\n", plan);
    }

    /** Searches the board; pass {@code ""} for a filter that is not being used. */
    private List<Map<String, Object>> search(String keyword, String category, String location) {
        return content(searchResponse(keyword, category, location));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map<String, Object>> searchResponse(String keyword, String category, String location) {
        return (ResponseEntity) rest.getForEntity(
                BOARD + "?q={q}&category={category}&location={location}", Map.class, keyword, category, location);
    }

    /** A complete, published vacancy with the given required skills. */
    private static Map<String, Object> published(String title, List<String> requiredSkills) {
        Map<String, Object> body = completeVacancy(title);
        body.put("requiredSkills", requiredSkills);
        body.put("preferredSkills", List.of());
        body.put("status", "PUBLISHED");
        return body;
    }

    /** A complete, published vacancy in the given department and location. */
    private static Map<String, Object> published(String title, String department, String location) {
        Map<String, Object> body = completeVacancy(title);
        body.put("department", department);
        body.put("location", location);
        body.put("status", "PUBLISHED");
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return content(response.getBody());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private static List<Object> titles(List<Map<String, Object>> cards) {
        return cards.stream().map(card -> card.get("title")).toList();
    }
}
