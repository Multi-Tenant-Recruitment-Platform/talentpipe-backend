package com.talentpipe.ai;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

final class AiRestTemplates {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private AiRestTemplates() {
        // utility
    }

    static RestTemplate build(AiServiceProperties properties, RestTemplateBuilder builder) {
        return builder
                .rootUri(properties.baseUrl())
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
    }
}
