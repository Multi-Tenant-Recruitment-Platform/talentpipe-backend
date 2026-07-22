package com.talentpipe.auth.service;

import com.talentpipe.auth.dto.AuthResponse;
import com.talentpipe.auth.dto.LoginRequest;
import com.talentpipe.auth.dto.RegisterRequest;
import com.talentpipe.auth.dto.RegisterResponse;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.RoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.common.exception.AccountLockedException;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.security.JwtTokenProvider;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use-cases: company onboarding, login, token refresh, logout
 * and current-user lookup.
 *
 * <p>Two distinct identities authenticate here and are never merged:</p>
 * <ul>
 *   <li><strong>Company users</strong> — rows in {@code users}, unique per
 *       (tenant, email), identified by the {@code X-Tenant-Subdomain} header.</li>
 *   <li><strong>Candidates</strong> — tenant-independent, globally unique
 *       email, no subdomain. Their credentials are checked inside the candidate
 *       module via {@link CandidateAuthService}; this service only ever sees a
 *       {@link CandidateProfile}, never a candidate row or password hash.</li>
 * </ul>
 *
 * <p>Failure semantics: bad credentials are always the same generic 401,
 * whichever step failed, so account existence never leaks. Two states are
 * deliberate exceptions and answer 403 with an actionable message —
 * unverified email and brute-force lockout — because both are only reachable
 * once the caller has already proven they know the password.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantService tenantService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final CandidateAuthService candidateAuthService;
    private final LoginAttemptService loginAttemptService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       TenantService tenantService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationService emailVerificationService,
                       CandidateAuthService candidateAuthService,
                       LoginAttemptService loginAttemptService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantService = tenantService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.candidateAuthService = candidateAuthService;
        this.loginAttemptService = loginAttemptService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * Company onboarding (PB-001): atomically creates the tenant and its first
     * COMPANY_ADMIN, who must verify their email before they can log in.
     *
     * @throws com.talentpipe.common.exception.DuplicateResourceException
     *         when the subdomain is already taken (409)
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        TenantResponse tenant = tenantService.createTenant(request.companyName(), request.subdomain());

        Role adminRole = roleRepository.findByName(RoleName.COMPANY_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "COMPANY_ADMIN role missing - did Flyway seed V3 run?"));

        User admin = userRepository.save(new User(
                tenant.id(),
                adminRole,
                normalizeEmail(request.admin().email()),
                passwordEncoder.encode(request.admin().password()),
                request.admin().firstName().trim(),
                request.admin().lastName().trim(),
                UserStatus.PENDING_VERIFICATION));

        emailVerificationService.issueAndSend(admin);

        log.info("Registered tenant '{}' (id={}) with initial admin {} - verification email queued",
                tenant.subdomain(), tenant.id(), admin.getId());
        return new RegisterResponse(tenant, userMapper.toResponse(admin, tenant.name()));
    }

    /**
     * Login (PB-007). The tenant comes from transport context (the
     * {@code X-Tenant-Subdomain} header this sprint) — never from the body.
     * Without that header the request is a global login: a candidate or a
     * SUPER_ADMIN.
     *
     * @throws BadCredentialsException     on any credential failure (401)
     * @throws AccountNotVerifiedException correct credentials, unverified email (403)
     * @throws AccountLockedException      too many failed attempts (403)
     */
    @Transactional
    public AuthResponse login(String subdomain, LoginRequest request) {
        String email = normalizeEmail(request.email());

        if (subdomain == null || subdomain.isBlank()) {
            return globalLogin(email, request.password());
        }

        TenantResponse tenant = tenantService.findBySubdomain(subdomain)
                .orElseThrow(AuthService::invalidCredentials);

        User user = userRepository.findByTenantIdAndEmail(tenant.id(), email)
                .orElseThrow(AuthService::invalidCredentials);

        authenticateUser(user, request.password());
        return issueTokens(user, tenant.name());
    }

    /**
     * Rotates a refresh token: validates it (signature, stored hash, revocation
     * state), revokes it, and returns a brand-new pair.
     *
     * <p>The owner may be a company user or a candidate — both identities share
     * the refresh-token table (ADR-4), so both are resolved here. Resolving
     * only against {@code users} would silently cap candidate sessions at the
     * access-token lifetime.</p>
     *
     * @throws InvalidTokenException on any validation failure (401)
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        UUID ownerId = refreshTokenService.validateAndConsume(rawRefreshToken);

        Optional<User> userOpt = userRepository.findById(ownerId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidTokenException("Token owner is no longer active");
            }
            return issueTokens(user, resolveTenantName(user.getTenantId()));
        }

        CandidateProfile candidate = candidateAuthService.findById(ownerId)
                .orElseThrow(() -> new InvalidTokenException("Token owner no longer exists"));
        if (!UserStatus.ACTIVE.name().equals(candidate.status())) {
            throw new InvalidTokenException("Token owner is no longer active");
        }
        return issueTokens(candidate);
    }

    /** Revokes the presented refresh token (idempotent, ownership-checked). */
    @Transactional
    public void logout(String rawRefreshToken, UUID currentUserId) {
        refreshTokenService.revoke(rawRefreshToken, currentUserId);
    }

    /** The authenticated principal's own profile, loaded fresh from the database. */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID principalId) {
        Optional<User> userOpt = userRepository.findById(principalId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return userMapper.toResponse(user, resolveTenantName(user.getTenantId()));
        }

        return candidateAuthService.findById(principalId)
                .map(AuthService::toUserResponse)
                .orElseThrow(() -> new InvalidTokenException("Token owner no longer exists"));
    }

    // ----------------------------------------------------------------- login

    /**
     * Login without a tenant: candidates first (the common case), then platform
     * SUPER_ADMINs, whose {@code tenant_id} is NULL.
     */
    private AuthResponse globalLogin(String email, String password) {
        Optional<CandidateProfile> candidate = candidateAuthService.authenticate(email, password);
        if (candidate.isPresent()) {
            return issueTokens(candidate.get());
        }

        User superAdmin = userRepository.findByTenantIdIsNullAndEmail(email)
                .orElseThrow(AuthService::invalidCredentials);

        authenticateUser(superAdmin, password);
        return issueTokens(superAdmin, null);
    }

    /**
     * Password check plus the account-state gates, in the order that keeps the
     * lock meaningful: a locked account is refused even with the right password,
     * and every wrong password counts towards the next lock.
     *
     * <p>Failure and success are recorded through {@link LoginAttemptService},
     * whose {@code REQUIRES_NEW} transactions commit the counter independently.
     * Mutating {@code user} directly here would be pointless — throwing
     * "invalid credentials" rolls this transaction back and takes the increment
     * with it, so the account could never actually lock.</p>
     */
    private void authenticateUser(User user, String rawPassword) {
        Instant now = Instant.now();

        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil(), now);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw invalidCredentials();
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotVerifiedException(
                    "Your email address is not verified yet. Check your inbox for the "
                            + "verification link, or request a new one.");
        }
        if (user.getStatus() == UserStatus.INVITED) {
            throw new AccountNotVerifiedException(
                    "This invitation has not been accepted yet. Use the link in your "
                            + "invitation email to set a password.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials(); // DISABLED — stays a generic 401
        }

        loginAttemptService.recordSuccess(user.getId());
    }

    // ---------------------------------------------------------------- tokens

    private AuthResponse issueTokens(User user, String tenantName) {
        String accessToken = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getTenantId(), user.getRole().getName().name(), user.getEmail());
        return new AuthResponse(
                accessToken,
                refreshTokenService.issue(user.getId()),
                jwtTokenProvider.getAccessTokenTtl().toSeconds(),
                userMapper.toResponse(user, tenantName));
    }

    /** Candidate tokens carry role CANDIDATE and no tenant claim. */
    private AuthResponse issueTokens(CandidateProfile candidate) {
        String accessToken = jwtTokenProvider.issueAccessToken(
                candidate.id(), null, RoleName.CANDIDATE.name(), candidate.email());
        return new AuthResponse(
                accessToken,
                refreshTokenService.issue(candidate.id()),
                jwtTokenProvider.getAccessTokenTtl().toSeconds(),
                toUserResponse(candidate));
    }

    // ------------------------------------------------------------------ util

    private static UserResponse toUserResponse(CandidateProfile candidate) {
        return new UserResponse(
                candidate.id(),
                null,
                null,
                RoleName.CANDIDATE.name(),
                candidate.email(),
                candidate.firstName(),
                candidate.lastName(),
                candidate.status(),
                candidate.createdAt());
    }

    private String resolveTenantName(UUID tenantId) {
        if (tenantId == null) {
            return null; // SUPER_ADMIN — no tenant
        }
        return tenantService.findById(tenantId).map(TenantResponse::name).orElse(null);
    }

    /** Emails are compared case-insensitively: normalize once at every boundary. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid credentials");
    }
}
