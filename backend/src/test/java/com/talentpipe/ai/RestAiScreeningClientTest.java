package com.talentpipe.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.talentpipe.ai.dto.AiScreeningRequest;
import com.talentpipe.ai.dto.AiScreeningRequest.CriterionWeight;
import com.talentpipe.ai.dto.AiScreeningResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;

@RestClientTest(RestAiScreeningClient.class)
@EnableConfigurationProperties(AiServiceProperties.class)
@TestPropertySource(properties = {
        "talentpipe.ai.base-url=http://ai-service:8000",
        "talentpipe.ai.timeout-seconds=30"
})
class RestAiScreeningClientTest {

    private static final String SCREEN_PATH = "/api/v1/screen";

    @Autowired
    private AiScreeningClient client;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    void screen_serializesSnakeCaseAndParsesResponse() {
        mockServer.expect(requestTo(SCREEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"cv_text\"")))
                .andExpect(content().string(containsString("\"job_description\"")))
                .andExpect(content().string(containsString("\"criteria_weights\"")))
                .andExpect(content().string(containsString("\"blind_mode\":true")))
                .andRespond(withSuccess("""
                        {
                          "overall_score": 88,
                          "summary": "Strong backend engineer.",
                          "strengths": ["Java 17", "Spring Boot"],
                          "gaps": ["Kubernetes"],
                          "criterion_scores": [
                            {"name": "Backend", "score": 88, "rationale": "6 years Java"}
                          ],
                          "model_version": "gemini-2.5-flash",
                          "prompt_version": "v1",
                          "cached": false
                        }
                        """, MediaType.APPLICATION_JSON));

        AiScreeningRequest request = new AiScreeningRequest(
                "Experienced Java engineer CV content",
                "Senior Java Backend Engineer",
                List.of(new CriterionWeight("Backend", 1.0)),
                true,
                false);

        AiScreeningResponse response = client.screen(request);

        mockServer.verify();
        assertThat(response.overallScore()).isEqualTo(88);
        assertThat(response.summary()).isEqualTo("Strong backend engineer.");
        assertThat(response.strengths()).containsExactly("Java 17", "Spring Boot");
        assertThat(response.criterionScores()).hasSize(1);
        assertThat(response.modelVersion()).isEqualTo("gemini-2.5-flash");
        assertThat(response.promptVersion()).isEqualTo("v1");
    }

    @Test
    void screen_rejectsNullRequestWithoutHttpCall() {
        assertThatThrownBy(() -> client.screen(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void screen_propagatesServerErrorWhenAiServiceFails() {
        mockServer.expect(requestTo(SCREEN_PATH))
                .andRespond(withServerError());

        AiScreeningRequest request = new AiScreeningRequest("CV", "JD", null, true, false);

        assertThatThrownBy(() -> client.screen(request))
                .isInstanceOf(HttpServerErrorException.class);
        mockServer.verify();
    }
}
