package com.talentpipe.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Python AI microservice.
 *
 * @param baseUrl        internal Docker/HTTP base URL ({@code AI_SERVICE_URL})
 * @param timeoutSeconds HTTP read timeout in seconds ({@code AI_SERVICE_TIMEOUT})
 */
@ConfigurationProperties(prefix = "talentpipe.ai")
public record AiServiceProperties(String baseUrl, int timeoutSeconds) {

    private static final String DEFAULT_BASE_URL = "http://ai-service:8000";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public AiServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
