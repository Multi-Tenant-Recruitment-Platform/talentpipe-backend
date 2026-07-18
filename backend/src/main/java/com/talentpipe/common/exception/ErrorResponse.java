package com.talentpipe.common.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * The uniform error envelope returned on EVERY failed request, from both MVC
 * exception handling and the security filter chain:
 *
 * <pre>{ timestamp, status, error, message, path }</pre>
 *
 * Status semantics (platform-wide contract):
 * 400 validation · 401 bad/expired token · 403 valid token, wrong role ·
 * 404 not found OR cross-tenant (existence must not leak across tenants) ·
 * 409 conflict · 422 business-rule violation · 500 unhandled.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
