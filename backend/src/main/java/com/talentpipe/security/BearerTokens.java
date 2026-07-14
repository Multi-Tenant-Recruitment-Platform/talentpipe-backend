package com.talentpipe.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** Single place that knows how a bearer token travels on a request. */
final class BearerTokens {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokens() {
    }

    /**
     * @return the raw JWT from the {@code Authorization: Bearer ...} header,
     *         or {@code null} when the header is absent or not a bearer scheme
     */
    static String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
