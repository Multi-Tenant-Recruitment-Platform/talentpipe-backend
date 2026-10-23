package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for the job board's structured filters: employment and
 * workplace type, experience, and pay. They run on the server over every
 * published vacancy. The board used to apply them in the browser, to the
 * page it had already loaded.
 */
@SuppressWarnings("unchecked")
class PublicJobBoardFiltersIntegrationTest extends AbstractJobIntegrationTest {

    private static final String BOARD = "/api/v1/public/jobs";
    private static final String LKR = "LKR — Sri Lankan rupee";

    @BeforeEach
    void fourAdverts() {
        Workspace acme = newWorkspace("Acme");
        publish(acme, "Graduate Engineer", "FULL_TIME", "ON_SITE", null, "MONTHLY", LKR, 100_000, 150_000);
        publish(acme, "Contract Architect", "CONTRACT", "REMOTE", 3, "MONTHLY", LKR, 200_000, 300_000);
        publish(acme, "Part-time Analyst", "PART_TIME", "HYBRID", 6, null, null, null, null);
        publish(acme, "Remote Developer", "FULL_TIME", "REMOTE", 2, "ANNUAL", "USD — US dollar", 50_000, null);
        // A draft never counts, whatever it says.
        Map<String, Object> draft = completeVacancy("Draft Engineer");
        draft.put("employmentType", "INTERNSHIP");
        create(acme.adminToken(), draft);
    }

    @Test
    void employmentAndWorkplaceType_matchAnyOfTheValuesGiven() {
        assertThat(titles("?employmentType=FULL_TIME,CONTRACT"))
                .containsExactlyInAnyOrder("Graduate Engineer", "Contract Architect", "Remote Developer");
        assertThat(titles("?employmentType=PART_TIME&employmentType=CONTRACT"))
                .containsExactlyInAnyOrder("Part-time Analyst", "Contract Architect");
        assertThat(titles("?workplaceType=REMOTE&employmentType=FULL_TIME")).containsExactly("Remote Developer");
    }

    @Test
    void experience_countsAnAdvertThatAsksForNoneAsZeroYears() {
        assertThat(titles("?maxExperience=0")).containsExactly("Graduate Engineer");
        assertThat(titles("?maxExperience=2"))
                .containsExactlyInAnyOrder("Graduate Engineer", "Remote Developer");
        assertThat(titles("?minExperience=5")).containsExactly("Part-time Analyst");
    }

    @Test
    void salary_matchesStatedPayThatOverlapsTheRange_withinOnePeriodAndCurrency() {
        assertThat(titles("?salaryOnly=true"))
                .containsExactlyInAnyOrder("Graduate Engineer", "Contract Architect", "Remote Developer");
        assertThat(titles("?payPeriod=MONTHLY&currency=lkr&salaryMin=140000&salaryMax=210000"))
                .containsExactlyInAnyOrder("Graduate Engineer", "Contract Architect");
        assertThat(titles("?payPeriod=MONTHLY&salaryMin=160000&salaryMax=190000")).isEmpty();
        // A one-sided salary is a range of one point.
        assertThat(titles("?payPeriod=ANNUAL&currency=USD&salaryMin=40000")).containsExactly("Remote Developer");
        assertThat(titles("?payPeriod=ANNUAL&salaryMax=40000")).isEmpty();
    }

    @Test
    void filters_combineWithTheKeyword_andTotalsCountTheWholeMatch() {
        // Both full-time adverts mention engineering (one in its title, one in
        // its description); the contract one is filtered out.
        ResponseEntity<Map<String, Object>> page = get(BOARD + "?q=engineer&employmentType=FULL_TIME&size=1");

        assertThat(page.getBody()).containsEntry("totalElements", 2);
        // One per page, the title match first.
        assertThat(titlesOf(page)).containsExactly("Graduate Engineer");
    }

    @Test
    void newest_overridesRelevance_forTheSameKeyword() {
        // By relevance the title match leads; newest first, the advert published last does.
        assertThat(titles("?q=engineer&employmentType=FULL_TIME"))
                .containsExactly("Graduate Engineer", "Remote Developer");
        assertThat(titles("?q=engineer&employmentType=FULL_TIME&sort=newest"))
                .containsExactly("Remote Developer", "Graduate Engineer");
    }

    @Test
    void theFilterOptions_offerEveryTypeAndSalarySpanInUse_withCounts() {
        Map<String, Object> options = get(BOARD + "/filters").getBody();

        assertThat((List<Map<String, Object>>) options.get("employmentTypes"))
                .extracting(option -> option.get("value"), option -> option.get("count"))
                .containsExactly(
                        tuple("FULL_TIME", 2),
                        tuple("CONTRACT", 1),
                        tuple("PART_TIME", 1));
        assertThat((List<Map<String, Object>>) options.get("workplaceTypes"))
                .extracting(option -> option.get("value"))
                .containsExactly("REMOTE", "HYBRID", "ON_SITE");
        assertThat((List<Map<String, Object>>) options.get("salaryRanges")).containsExactly(
                Map.of("payPeriod", "MONTHLY", "currency", "LKR", "min", 100000.0, "max", 300000.0, "count", 2),
                Map.of("payPeriod", "ANNUAL", "currency", "USD", "min", 50000.0, "max", 50000.0, "count", 1));
    }

    @Test
    void contradictoryOrUnknownFilters_areRejectedWithTheReason() {
        assertThat(get(BOARD + "?employmentType=FOREVER").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(BOARD + "?minExperience=5&maxExperience=1").getBody())
                .containsEntry("code", "INVALID_REQUEST");
        assertThat(get(BOARD + "?salaryMin=100").getBody())
                .containsEntry("message",
                        "Choose a payPeriod with a salary range: hourly, monthly and annual pay don't compare.");
    }

    // ---------------------------------------------------------------- helpers

    private void publish(Workspace workspace, String title, String employmentType, String workplaceType,
                         Integer years, String payPeriod, String currency, Integer salaryMin, Integer salaryMax) {
        Map<String, Object> body = completeVacancy(title);
        body.put("employmentType", employmentType);
        body.put("workplaceType", workplaceType);
        body.put("minimumExperienceYears", years);
        body.put("payPeriod", payPeriod);
        body.put("currency", currency);
        body.put("salaryMin", salaryMin);
        body.put("salaryMax", salaryMax);
        body.put("status", "PUBLISHED");
        create(workspace.adminToken(), body);
    }

    private List<Object> titles(String query) {
        ResponseEntity<Map<String, Object>> page = get(BOARD + query);
        assertThat(page.getStatusCode()).as("board%s: %s", query, page.getBody()).isEqualTo(HttpStatus.OK);
        return titlesOf(page);
    }

    private static List<Object> titlesOf(ResponseEntity<Map<String, Object>> page) {
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .map(card -> card.get("title"))
                .toList();
    }
}
