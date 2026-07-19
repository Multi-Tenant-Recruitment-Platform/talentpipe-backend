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
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.security.JwtTokenProvider;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.util.Locale;
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
 * <p>Cross-module collaboration happens strictly through {@link TenantService}
 * and its DTOs — this module never touches the Tenant entity. Login failures
 * are always the same generic 401 regardless of which step failed (unknown
 * subdomain, unknown user, wrong password, disabled account), so nothing about
 * account existence leaks.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantService tenantService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final com.talentpipe.candidate.repository.CandidateRepository candidateRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       TenantService tenantService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationService emailVerificationService,
                       com.talentpipe.candidate.repository.CandidateRepository candidateRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantService = tenantService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.candidateRepository = candidateRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * Company onboarding (PB-001): atomically creates the tenant and its
     * first COMPANY_ADMIN user.
     *
     * @throws com.talentpipe.common.exception.DuplicateResourceException
     *         when the subdomain is already taken (409)
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        TenantResponse tenant = tenantService.createTenant(request.companyName(), request.subdomain());

        Role adminRole = roleRepository.findByName(RoleName.COMPANY_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "COMPANY_ADMIN role missing — did Flyway seed V3 run?"));

        // Account starts PENDING_VERIFICATION; it becomes ACTIVE only after the
        // user clicks the verification link sent by emailVerificationService (PB-001).
        User admin = new User(
                tenant.id(),
                adminRole,
                normalizeEmail(request.admin().email()),
                passwordEncoder.encode(request.admin().password()),
                request.admin().firstName().trim(),
                request.admin().lastName().trim(),
                UserStatus.PENDING_VERIFICATION);
        admin = userRepository.save(admin);

        emailVerificationService.issueAndSend(admin);

        log.info("Registered tenant '{}' (id={}) with initial admin (userId={}) — verification email sent",
                tenant.subdomain(), tenant.id(), admin.getId());
        return new RegisterResponse(tenant, userMapper.toResponse(admin, tenant.name()));
    }

    /**
     * Login (PB-007). The tenant is resolved from the subdomain supplied by
     * transport context (X-Tenant-Subdomain header this sprint) — never from
     * the request body.
     *
     * @throws BadCredentialsException on ANY failure — deliberately
     *         indistinguishable to the caller (401)
     */
    @Transactional
    public AuthResponse login(String subdomain, LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        // Global login (candidates or super admins) — no subdomain header.
        if (subdomain == null || subdomain.isBlank()) {
            // 1. Try candidate first
            var candidateOpt = candidateRepository.findByEmail(normalizedEmail);
            if (candidateOpt.isPresent()) {
                var candidate = candidateOpt.get();
                if (!passwordEncoder.matches(request.password(), candidate.getPasswordHash())) {
                    throw invalidCredentials();
                }
                if (candidate.getStatus() != UserStatus.ACTIVE) {
                    throw invalidCredentials();
                }
                return issueTokens(candidate);
            }

            // 2. Try platform SUPER_ADMIN (users with tenant_id IS NULL)
            var userOpt = userRepository.findByTenantIdAndEmail(null, normalizedEmail);
            if (userOpt.isPresent()) {
                var user = userOpt.get();
                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                    throw invalidCredentials();
                }
                if (user.getStatus() != UserStatus.ACTIVE) {
                    throw invalidCredentials();
                }
                return issueTokens(user, null);
            }

            throw invalidCredentials();
        }

        // Company / Tenant Login
        TenantResponse tenant = tenantService.findBySubdomain(subdomain)
                .orElseThrow(AuthService::invalidCredentials);

        User user = userRepository
                .findByTenantIdAndEmail(tenant.id(), normalizedEmail)
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // TODO(sprint2): increment failed_login_count and enforce locked_until
            // (account lockout / brute-force handling — columns already exist in schema).
            throw invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            // PENDING_VERIFICATION, DISABLED, and INVITED accounts must not authenticate.
            // Same generic 401 — never reveal which check failed.
            throw invalidCredentials();
        }

        return issueTokens(user, tenant.name());
    }

    /**
     * Rotates a refresh token: validates it (signature + stored hash +
     * revocation state), revokes it, and returns a brand-new token pair.
     *
     * @throws InvalidTokenException on any validation failure (401)
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        UUID userId = refreshTokenService.validateAndConsume(rawRefreshToken);

        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidTokenException("Token owner no longer active"));

        return issueTokens(user, resolveTenantName(user.getTenantId()));
    }

    /** Revokes the presented refresh token (idempotent, ownership-checked). */
    @Transactional
    public void logout(String rawRefreshToken, UUID currentUserId) {
        refreshTokenService.revoke(rawRefreshToken, currentUserId);
    }

    /** Current authenticated user's profile, loaded fresh from the database. */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        var userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return userMapper.toResponse(user, resolveTenantName(user.getTenantId()));
        }

        var candidate = candidateRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Token owner no longer exists"));
        return mapCandidateToUserResponse(candidate);
    }

    // ------------------------------------------------------------------ util

    private AuthResponse issueTokens(User user, String tenantName) {
        String accessToken = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getTenantId(), user.getRole().getName().name(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenTtl().toSeconds(),
                userMapper.toResponse(user, tenantName));
    }

    private AuthResponse issueTokens(com.talentpipe.candidate.entity.Candidate candidate) {
        String accessToken = jwtTokenProvider.issueAccessToken(
                candidate.getId(), null, RoleName.CANDIDATE.name(), candidate.getEmail());
        String refreshToken = refreshTokenService.issue(candidate.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenTtl().toSeconds(),
                mapCandidateToUserResponse(candidate));
    }

    private UserResponse mapCandidateToUserResponse(com.talentpipe.candidate.entity.Candidate candidate) {
        String fullName = candidate.getFullName();
        String firstName = fullName;
        String lastName = "";
        int lastSpaceIdx = fullName.lastIndexOf(' ');
        if (lastSpaceIdx > 0) {
            firstName = fullName.substring(0, lastSpaceIdx).trim();
            lastName = fullName.substring(lastSpaceIdx).trim();
        }
        return new UserResponse(
                candidate.getId(),
                null,
                null,
                RoleName.CANDIDATE.name(),
                candidate.getEmail(),
                firstName,
                lastName,
                candidate.getStatus().name(),
                candidate.getCreatedAt());
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
