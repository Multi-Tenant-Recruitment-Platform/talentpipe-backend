package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.demo.DemoDataSeeder;
import com.talentpipe.security.Permission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * The demo data loads cleanly through the real services, only once, and the
 * logins in {@code docs/DEMO.md} work the way the presentation relies on.
 */
@ActiveProfiles("demo")
@SuppressWarnings("unchecked")
class DemoDataSeederIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Value("${talentpipe.demo.password}")
    private String demoPassword;

    @Test
    void theDemo_loadsOnce_andEveryKindOfLoginWorks() {
        // The seeder already ran at startup; each test starts from an empty database.
        assertThat(seeder.seedIfEmpty()).isTrue();
        assertThat(seeder.seedIfEmpty()).as("second run").isFalse();

        // The Owner signs in the way the sign-in modal does (no workspace header).
        Map<String, Object> owner = signIn("dilini.wickramasinghe@lumora.test");
        assertThat((Map<String, Object>) owner.get("user"))
                .containsEntry("roleName", "Owner")
                .containsEntry("tenantName", "Lumora Technologies");
        assertThat((List<String>) ((Map<String, Object>) owner.get("user")).get("permissions"))
                .hasSize(Permission.values().length);

        // The public board: fifteen live adverts from three companies.
        assertThat(get("/api/v1/public/jobs").getBody()).containsEntry("totalElements", 15);

        // An interviewer sees only the vacancies they work on.
        String arjun = (String) signIn("arjun.selvaraj@lumora.test").get("accessToken");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs", arjun, null).getBody()).containsEntry("totalElements", 4);

        // A recruiter sees the whole company's, applicants included.
        String kasun = (String) signIn("kasun.perera@lumora.test").get("accessToken");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs", kasun, null).getBody()).containsEntry("totalElements", 11);
        assertThat(callForList("/api/v1/applications/tenant", kasun).getBody()).hasSize(13);

        // A candidate sees their own applications.
        String ashan = (String) signIn("ashan.gunasekara@mail.test").get("accessToken");
        assertThat(callForList("/api/v1/applications", ashan).getBody()).hasSize(2);

        // The consultant is in two companies and is a candidate: the picker appears.
        ResponseEntity<Map<String, Object>> consultant =
                loginResponse(null, "sanduni.karunaratne@talentconsult.test", demoPassword);
        assertThat(consultant.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((List<?>) ((Map<String, Object>) consultant.getBody().get("details")).get("accounts")).hasSize(3);
    }

    private Map<String, Object> signIn(String email) {
        ResponseEntity<Map<String, Object>> response = loginResponse(null, email, demoPassword);
        assertThat(response.getStatusCode()).as("sign-in as %s: %s", email, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
