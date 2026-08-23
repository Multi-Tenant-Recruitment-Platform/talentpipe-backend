package com.talentpipe.common.exception;

/**
 * Maps to 400. Thrown when a request passes bean validation but is still
 * invalid once the service layer has normalized it — the case bean validation
 * structurally cannot catch, because it only ever sees the raw input.
 *
 * <p>Example: {@code name} of {@code "<b></b>"} satisfies {@code @NotBlank}
 * (the raw string is not blank) but normalizes to nothing once the markup is
 * stripped, and {@code name} is a NOT NULL column. Without this the request
 * would reach the database and surface as an opaque 409.</p>
 *
 * <p>Distinct from {@link BusinessRuleException} (422): that one signals a
 * well-formed request refused because of the tenant's business state, this one
 * signals input that is simply not valid.</p>
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
