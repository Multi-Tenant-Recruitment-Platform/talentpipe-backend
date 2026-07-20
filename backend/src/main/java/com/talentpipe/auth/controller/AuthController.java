package com.talentpipe.auth.controller;

import com.talentpipe.auth.dto.AcceptInviteRequest;
import com.talentpipe.auth.dto.AuthResponse;
import com.talentpipe.auth.dto.ForgotPasswordRequest;
import com.talentpipe.auth.dto.LoginRequest;
import com.talentpipe.auth.dto.PasswordResetConfirmRequest;
import com.talentpipe.auth.dto.RefreshTokenRequest;
import com.talentpipe.auth.dto.RegisterRequest;
import com.talentpipe.auth.dto.RegisterResponse;
import com.talentpipe.auth.dto.ResendVerificationRequest;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.dto.VerifyEmailRequest;
import com.talentpipe.auth.service.AuthService;
import com.talentpipe.auth.service.EmailVerificationService;
import com.talentpipe.auth.service.InvitationService;
import com.talentpipe.auth.service.PasswordResetService;
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.candidate.service.CandidateVerificationService;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.security.UserPrincipal;
import com.talentpipe.tenant.service.TenantService;
import jakarta.validation.Valid;
import java.util.Locale;
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
 * <p>register / login / refresh / verify-email / resend-verification /
 * forgot-password / password-reset / accept-invite are public (see
 * SecurityConfig); logout and /me require a valid access token.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * Tenant resolution for login: localhost has no real subdomains, so the SPA
     * sends the company subdomain in this header. Production derives it from the
     * Host header instead — see docs/DECISIONS.md (ADR-1). Absent header means a
     * global login (candidate or SUPER_ADMIN).
     */
    static final String TENANT_SUBDOMAIN_HEADER = "X-Tenant-Subdomain";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final CandidateVerificationService candidateVerificationService;
    private final CandidateAuthService candidateAuthService;
    private final PasswordResetService passwordResetService;
    private final InvitationService invitationService;
    private final TenantService tenantService;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          CandidateVerificationService candidateVerificationService,
                          CandidateAuthService candidateAuthService,
                          PasswordResetService passwordResetService,
                          InvitationService invitationService,
                          TenantService tenantService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.candidateVerificationService = candidateVerificationService;
        this.candidateAuthService = candidateAuthService;
        this.passwordResetService = passwordResetService;
        this.invitationService = invitationService;
        this.tenantService = tenantService;
    }

    /** Company onboarding (PB-001): creates a tenant plus its first COMPANY_ADMIN. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Login (PB-007). Tenant from the header (optional), credentials from the body. */
    @PostMapping("/login")
    public AuthResponse login(
            @RequestHeader(value = TENANT_SUBDOMAIN_HEADER, required = false) String tenantSubdomain,
            @Valid @RequestBody LoginRequest request) {
        return authService.login(tenantSubdomain, request);
    }

    /** Exchanges a valid refresh token for a new pair (rotation). */
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

    /** The authenticated principal's own profile, resolved from the access token. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.getCurrentUser(principal.id());
    }

    // ------------------------------------------------------------------ PB-001

    /**
     * Verifies an email address and activates the account.
     *
     * <p>One link works for both identities: company-user tokens are tried
     * first, then candidate tokens. Each service reports whether the token was
     * one of its own, so an unknown token ends as a single clean 401 rather
     * than leaking which population it belonged to.</p>
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        boolean handled = emailVerificationService.verify(request.token())
                || candidateVerificationService.verify(request.token());
        if (!handled) {
            throw new InvalidTokenException("Unknown or invalid verification token");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Re-sends a verification email (PB-001). Always 200: whether the address
     * exists, and whether it is already verified, must not be observable.
     * The tenant, when relevant, travels in the header as it does at login.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @RequestHeader(value = TENANT_SUBDOMAIN_HEADER, required = false) String tenantSubdomain,
            @Valid @RequestBody ResendVerificationRequest request) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (tenantSubdomain == null || tenantSubdomain.isBlank()) {
            candidateAuthService.resendVerification(email);
        } else {
            tenantService.findBySubdomain(tenantSubdomain).ifPresent(
                    tenant -> emailVerificationService.resend(tenant.id(), email));
        }
        return ResponseEntity.ok().build();
    }

    // ------------------------------------------------------------------ PB-008

    /**
     * Starts a password reset. Always 200 — the response is identical whether
     * or not the email is registered, so it cannot be used to probe for
     * accounts. Every account using that address receives its own link.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    /**
     * Completes a password reset: validates the 30-minute single-use token,
     * stores a new bcrypt hash, and revokes every active refresh token.
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    // ------------------------------------------------------- PB-003 / PB-004

    /**
     * Accepts a team invitation by setting the first password, activating the
     * account in the tenant and role it was invited into.
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        invitationService.accept(request.token(), request.password());
        return ResponseEntity.ok().build();
    }
}
