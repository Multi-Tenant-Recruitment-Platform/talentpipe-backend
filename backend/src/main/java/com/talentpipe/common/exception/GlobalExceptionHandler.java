package com.talentpipe.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception that escapes a controller into the platform's
 * uniform error envelope ({@link ErrorResponse}). No other error shape may
 * ever reach a client.
 *
 * <p>Status mapping contract: 400 validation · 401 authentication/token ·
 * 403 authorization · 404 missing OR cross-tenant · 409 conflict ·
 * 422 business rule · 500 unhandled (logged with a correlation id).</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------ 400

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return envelope(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return envelope(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        return envelope(HttpStatus.BAD_REQUEST,
                "Missing required header: " + ex.getHeaderName(), request);
    }

    // ------------------------------------------------------------------ 401

    /**
     * Covers Spring Security's {@code BadCredentialsException} (wrong
     * email/password) and any other authentication failure raised from
     * service code. The message stays generic on purpose: never reveal
     * whether the tenant, the user or the password was the wrong part.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return envelope(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex,
                                                            HttpServletRequest request) {
        return envelope(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request);
    }

    // ------------------------------------------------------------------ 403

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return envelope(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    // ------------------------------------------------------------------ 404

    /**
     * 404 is returned both for genuinely missing resources and for resources
     * owned by another tenant — deliberately indistinguishable so existence
     * does not leak across tenant boundaries.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return envelope(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                          HttpServletRequest request) {
        return envelope(HttpStatus.NOT_FOUND, "Resource not found", request);
    }

    // ------------------------------------------------------------------ 405

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return envelope(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------ 409

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex,
                                                         HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Backstop for races that slip past application-level uniqueness checks
     * (e.g. two concurrent registrations of the same subdomain) and hit a
     * database constraint instead. The constraint detail stays server-side.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, "Request conflicts with existing data", request);
    }

    // ------------------------------------------------------------------ 422

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex,
                                                            HttpServletRequest request) {
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------ 500

    /**
     * Last-resort handler. The client receives only an opaque correlation id;
     * the full stack trace is logged server-side under that id so incidents
     * can be traced without leaking internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [correlationId={}] on {} {}",
                correlationId, request.getMethod(), request.getRequestURI(), ex);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Reference: " + correlationId, request);
    }

    // ------------------------------------------------------------------ util

    private ResponseEntity<ErrorResponse> envelope(HttpStatus status, String message,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
