package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Shared plumbing for the job module's integration tests: on top of the
 * workspace helpers of {@link AbstractWorkspaceIntegrationTest}, building
 * vacancies and moving them through their lifecycle via the real API.
 */
abstract class AbstractJobIntegrationTest extends AbstractWorkspaceIntegrationTest {

    /**
     * A vacancy body that satisfies every publish rule, as a mutable map so a
     * test can change or remove exactly the field it is about. The deadline is
     * 30 days out, so it is valid whenever the suite runs.
     */
    protected static Map<String, Object> completeVacancy(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("department", "Engineering");
        body.put("openings", 2);
        body.put("employmentType", "FULL_TIME");
        body.put("workplaceType", "HYBRID");
        body.put("location", "Colombo, Sri Lanka");
        body.put("applicationDeadline", LocalDate.now().plusDays(30).toString());
        body.put("jobSummary", "Lead the services behind our candidate pipeline.");
        body.put("jobDescription", "You will own the matching service.\n\nWe value pragmatic engineering.");
        body.put("keyResponsibilities", List.of("Own the matching service"));
        body.put("requiredSkills", List.of("Java", "Spring Boot"));
        body.put("preferredSkills", List.of("Kubernetes"));
        body.put("minimumExperienceYears", 4);
        body.put("certifications", List.of());
        body.put("languageRequirements", List.of("English"));
        body.put("benefits", List.of("HEALTH_INSURANCE"));
        body.put("workingDays", List.of("MON", "TUE", "WED", "THU", "FRI"));
        body.put("workingHours", "9:00 AM – 6:00 PM");
        body.put("shiftType", "DAY");
        body.put("expectedHoursPerWeek", 40);
        body.put("recruitmentPipelineId", "TECHNICAL");
        body.put("screeningQuestions", List.of("Do you have the right to work in Sri Lanka?"));
        body.put("status", "DRAFT");
        return body;
    }

    /** Creates a vacancy and returns the stored response; fails the test unless it answers 201. */
    protected Map<String, Object> create(String token, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/api/v1/jobs", token, body);
        assertThat(response.getStatusCode()).as("create: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** Creates a complete vacancy already PUBLISHED. */
    protected Map<String, Object> createPublished(String token, String title) {
        Map<String, Object> body = completeVacancy(title);
        body.put("status", "PUBLISHED");
        return create(token, body);
    }

    /** {@code POST /jobs/{id}/{move}} — publish, close, archive or duplicate. */
    protected ResponseEntity<Map<String, Object>> move(String token, Object vacancyId, String move) {
        return call(HttpMethod.POST, "/api/v1/jobs/" + vacancyId + "/" + move, token, null);
    }

    /** Walks a vacancy forward through the given moves, asserting each succeeds. */
    protected void advance(String token, Object vacancyId, String... moves) {
        for (String move : moves) {
            assertThat(move(token, vacancyId, move).getStatusCode()).as(move).isEqualTo(HttpStatus.OK);
        }
    }
}
