package com.talentpipe.application.screening;

import com.talentpipe.ai.AiScreeningClient;
import com.talentpipe.ai.dto.AiScreeningRequest;
import com.talentpipe.ai.dto.AiScreeningResponse;
import com.talentpipe.application.entity.CriterionScore;
import com.talentpipe.application.entity.ScreeningResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/** Screens through the AI service ({@code talentpipe.ai.screening-mode=remote}, the default). */
@Component
@ConditionalOnProperty(name = "talentpipe.ai.screening-mode", havingValue = "remote", matchIfMissing = true)
public class RemoteApplicationScreener implements ApplicationScreener {

    private final AiScreeningClient client;

    public RemoteApplicationScreener(AiScreeningClient client) {
        this.client = client;
    }

    @Override
    public ScreeningResult screen(ScreeningInput input) {
        if (!input.hasCvText()) {
            throw new ScreeningUnavailableException("The CV's text is not available, so it could not be screened.");
        }
        AiScreeningRequest request = new AiScreeningRequest(input.cvText(), input.jobDescription(),
                input.criteria().stream()
                        .map(criterion -> new AiScreeningRequest.CriterionWeight(criterion.name(), criterion.weight()))
                        .toList(),
                input.blindMode(), false);
        AiScreeningResponse response;
        try {
            response = client.screen(request);
        } catch (RestClientException ex) {
            // The type only: an HTTP client's message can quote the response body.
            throw new ScreeningUnavailableException("The AI screening service could not be reached.", ex);
        }
        if (response == null) {
            throw new ScreeningUnavailableException("The AI screening service returned no result.");
        }
        List<CriterionScore> criteria = response.criterionScores() == null ? List.of()
                : response.criterionScores().stream()
                        .map(score -> new CriterionScore(score.name(), score.score(), score.rationale()))
                        .toList();
        return new ScreeningResult(response.overallScore(), response.summary(), response.strengths(),
                response.gaps(), criteria, response.modelVersion(), response.promptVersion(), response.cached());
    }
}
