package com.talentpipe.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * The uniform error envelope returned on EVERY failed request, from both MVC
 * exception handling and the security filter chain:
 *
 * <pre>{ timestamp, status, error, code, message, path [, fieldErrors] [, details] }</pre>
 *
 * <ul>
 *   <li>{@code code} — stable machine-readable reason ({@link ErrorCode}).
 *       Clients branch on this, never on {@code message}.</li>
 *   <li>{@code message} — a sentence for people. Unchanged in shape from the
 *       original envelope, so existing clients keep working.</li>
 *   <li>{@code fieldErrors} — present only on validation failures: one entry
 *       per invalid field, so a form can put each message next to its input.</li>
 *   <li>{@code details} — present only for codes that need a payload, e.g.
 *       the account choices of {@link ErrorCode#WORKSPACE_SELECTION_REQUIRED}.</li>
 * </ul>
 *
 * Status semantics (platform-wide contract):
 * 400 validation · 401 bad/expired token · 403 valid token, missing permission ·
 * 404 not found OR cross-tenant (existence must not leak across tenants) ·
 * 409 conflict · 422 business-rule violation · 500 unhandled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors,
        Object details
) {

    /** An envelope with the status's default code. */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, ErrorCode.defaultFor(status), message, path);
    }

    public static ErrorResponse of(HttpStatus status, ErrorCode code, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, path, null, null);
    }

    /** The same envelope carrying per-field validation messages. */
    public ErrorResponse withFieldErrors(List<FieldErrorDetail> errors) {
        return new ErrorResponse(timestamp, status, error, code, message, path, List.copyOf(errors), details);
    }

    /** The same envelope carrying a code-specific payload. */
    public ErrorResponse withDetails(Object payload) {
        return new ErrorResponse(timestamp, status, error, code, message, path, fieldErrors, payload);
    }

    /**
     * One invalid request field.
     *
     * @param field   the field's path in the request body, e.g. {@code admin.password}
     * @param message what is wrong, as a sentence for the person filling the form
     */
    public record FieldErrorDetail(String field, String message) {
    }
}
