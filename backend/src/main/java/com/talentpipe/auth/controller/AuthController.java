package com.talentpipe.auth.controller;

import com.talentpipe.auth.dto.AuthResponse;
import com.talentpipe.auth.dto.LoginRequest;
import com.talentpipe.auth.dto.PasswordResetConfirmRequest;
import com.talentpipe.auth.dto.PasswordResetRequestDto;
import com.talentpipe.auth.dto.RefreshTokenRequest;
import com.talentpipe.auth.dto.RegisterRequest;
import com.talentpipe.auth.dto.RegisterResponse;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.dto.VerifyEmailRequest;
import com.talentpipe.auth.service.AuthService;
import com.talentpipe.auth.service.EmailVerificationService;
import com.talentpipe.auth.service.PasswordResetService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Controllers speak DTOs exclusively — entities and
 * password hashes never cross this boundary.
 *
 * <p>register / login / refresh are public (see SecurityConfig); logout and
 * /me require a valid access token.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * Week 1 tenant resolution for login: localhost has no real subdomains,
     * so the SPA sends the company subdomain in this header. Production
     * derives it from the Host header instead — see docs/DECISIONS.md (ADR-1).
     */
    static final String TENANT_SUBDOMAIN_HEADER = "X-Tenant-Subdomain";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    /** Company onboarding (PB-001): creates a tenant plus its first COMPANY_ADMIN. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Login (PB-007). Tenant comes from the header (optional), credentials from the body. */
    @PostMapping("/login")
    public AuthResponse login(@RequestHeader(value = TENANT_SUBDOMAIN_HEADER, required = false) String tenantSubdomain,
                              @Valid @RequestBody LoginRequest request) {
        return authService.login(tenantSubdomain, request);
    }

    /** Exchanges a valid refresh token for a new token pair (rotation). */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Revokes the presented refresh token. Idempotent. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken(), principal.id());
        return ResponseEntity.noContent().build();
    }

    /** The authenticated user's own profile, resolved from the access token. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.getCurrentUser(principal.id());
    }

    // ------------------------------------------------------------------ PB-001

    /**
     * Verifies a user's email and activates their account (PB-001).
     * The token is the raw value from the emailed link.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok().build();
    }

    // ------------------------------------------------------------------ PB-008

    /**
     * Initiates a password reset: issues a 30-minute token and logs the reset
     * link (Sprint 1: console). Always returns 200 — the response is
     * indistinguishable whether or not the email/subdomain exist, to prevent
     * user enumeration.
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestDto request) {
        passwordResetService.requestReset(request.email(), request.subdomain());
        return ResponseEntity.ok().build();
    }

    /**
     * Confirms a password reset: validates the token (must not be expired or
     * used), updates the password hash, and revokes all active refresh tokens.
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
