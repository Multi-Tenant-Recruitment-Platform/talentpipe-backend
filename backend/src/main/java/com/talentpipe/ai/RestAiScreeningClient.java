package com.talentpipe.ai;

import com.talentpipe.ai.dto.AiScreeningRequest;
import com.talentpipe.ai.dto.AiScreeningResponse;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * REST adapter calling the Python FastAPI screening endpoint.
 */
@Component
public class RestAiScreeningClient implements AiScreeningClient {

    private final RestTemplate restTemplate;

    public RestAiScreeningClient(AiServiceProperties properties, RestTemplateBuilder builder) {
        this.restTemplate = AiRestTemplates.build(properties, builder);
    }

    @Override
    public AiScreeningResponse screen(AiScreeningRequest request) {
        Objects.requireNonNull(request, "request");
        return restTemplate.postForObject("/api/v1/screen", request, AiScreeningResponse.class);
    }
}
