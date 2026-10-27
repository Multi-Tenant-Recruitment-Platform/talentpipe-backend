package com.talentpipe.auth.service;

import com.talentpipe.auth.dto.AuthResponse;
import com.talentpipe.auth.dto.LoginRequest;
import com.talentpipe.auth.dto.RegisterRequest;
import com.talentpipe.auth.dto.RegisterResponse;
import com.talentpipe.auth.dto.SelectWorkspaceRequest;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.AccountType;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.Choice;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.dto.CandidateCredentialCheck;
import com.talentpipe.candidate.dto.CandidateCredentialCheck.Outcome;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.common.exception.AccountLockedException;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.exception.WorkspaceSelectionRequiredException;
import com.talentpipe.security.JwtTokenProvider;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 *       (tenant, email), identified by the {@code X-Tenant-Subdomain} header,
 *       or without it by the person choosing among the accounts their
 *       credentials fit.</li>
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

    /** What a candidate's role is called in responses; candidates hold no company role. */
    static final String CANDIDATE_ROLE_NAME = "Candidate";

    private final UserRepository userRepository;
    private final TenantRoleService tenantRoleService;
    private final TenantService tenantService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final CandidateAuthService candidateAuthService;
    private final LoginAttemptService loginAttemptService;
    private final LoginSelectionService loginSelectionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository,
                       TenantRoleService tenantRoleService,
                       TenantService tenantService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationService emailVerificationService,
                       CandidateAuthService candidateAuthService,
                       LoginAttemptService loginAttemptService,
                       LoginSelectionService loginSelectionService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.tenantRoleService = tenantRoleService;
        this.tenantService = tenantService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.candidateAuthService = candidateAuthService;
        this.loginAttemptService = loginAttemptService;
        this.loginSelectionService = loginSelectionService;
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

        // Every workspace starts with Owner, Recruiter and Interviewer; the
        // person who registers it is its first Owner.
        TenantRole owner = tenantRoleService.provisionDefaultRoles(tenant.id()).get(SystemRole.OWNER);

        User admin = userRepository.save(new User(
                tenant.id(),
                owner,
                tenantRoleService.legacyRoleFor(owner),
                normalizeEmail(request.admin().email()),
                passwordEncoder.encode(request.admin().password()),
                request.admin().firstName().trim(),
                request.admin().lastName().trim(),
                UserStatus.PENDING_VERIFICATION));

        emailVerificationService.issueAndSend(admin);

        log.info("Registered tenant '{}' (id={}) with initial admin {} - verification email queued",
                tenant.subdomain(), tenant.id(), admin.getId());
        return new RegisterResponse(tenant, userMapper.toSessionResponse(admin, tenant.name()));
    }

    /**
     * Login (PB-007). The tenant comes from transport context (the
     * {@code X-Tenant-Subdomain} header this sprint) — never from the body.
     * Without that header, which is how the sign-in modal calls it, the
     * credentials are tried against every account using the email; see
     * {@link #loginWithoutWorkspace}.
     *
     * @throws BadCredentialsException     on any credential failure (401)
     * @throws AccountNotVerifiedException correct credentials, unverified email
     *                                     or unaccepted invitation (403)
     * @throws AccountLockedException      too many failed attempts (403)
     * @throws WorkspaceSelectionRequiredException the credentials fit several
     *                                     accounts and no workspace was named (409)
     */
    @Transactional
    public AuthResponse login(String subdomain, LoginRequest request) {
        String email = normalizeEmail(request.email());

        if (subdomain == null || subdomain.isBlank()) {
            return loginWithoutWorkspace(email, request.password());
        }

        TenantResponse tenant = tenantService.findBySubdomain(subdomain)
                .orElseThrow(AuthService::invalidCredentials);

        User user = userRepository.findByTenantIdAndEmail(tenant.id(), email)
                .orElseThrow(AuthService::invalidCredentials);

        authenticateUser(user, request.password());
        return issueTokens(user, tenant.name());
    }

    /**
     * Second step of a sign-in whose credentials fit several accounts: signs
     * in to the one the person chose. The password is not sent again; the
     * token proves it was checked against this account moments ago. Lock and
     * status are checked again, since either may have changed in between.
     *
     * @throws InvalidTokenException       unknown, used or expired token (401)
     * @throws com.talentpipe.common.exception.InvalidRequestException
     *                                     the account was not one of the choices (400)
     * @throws AccountNotVerifiedException the chosen account is not ready yet (403)
     * @throws AccountLockedException      the chosen account is locked (403)
     */
    @Transactional
    public AuthResponse selectWorkspace(SelectWorkspaceRequest request) {
        UUID accountId = loginSelectionService.redeem(request.selectionToken(), request.accountId());

        Optional<User> userOpt = userRepository.findById(accountId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            requireNotLocked(user, Instant.now());
            admit(user);
            return issueTokens(user, resolveTenantName(user.getTenantId()));
        }

        return candidateAuthService.completeSignIn(accountId)
                .map(this::issueTokens)
                .orElseThrow(AuthService::invalidCredentials);
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
            return userMapper.toSessionResponse(user, resolveTenantName(user.getTenantId()));
        }

        return candidateAuthService.findById(principalId)
                .map(AuthService::toUserResponse)
                .orElseThrow(() -> new InvalidTokenException("Token owner no longer exists"));
    }

    // ----------------------------------------------------------------- login

    /**
     * Sign-in that names no workspace. The credentials are checked against
     * every account using the email: memberships of any company, a platform
     * administrator, and the candidate account.
     *
     * <ul>
     *   <li>One match signs in to it, exactly as naming its workspace would.</li>
     *   <li>Several matches answer {@code WORKSPACE_SELECTION_REQUIRED} with
     *       the choices. Nothing is signed in, and no attempt is recorded,
     *       until the person picks one ({@link #selectWorkspace}).</li>
     *   <li>No match counts a failure against every account the password was
     *       checked against (each guess tests all of them) and answers the
     *       generic 401. When every account is locked the lock is reported
     *       instead, as it is when naming the workspace.</li>
     * </ul>
     *
     * <p>Locked accounts are never checked. If a password that fit a locked
     * account answered differently from one that did not, this path would be
     * a way to keep guessing past the lock. Disabled accounts are left out:
     * they can never be signed in to.</p>
     */
    private AuthResponse loginWithoutWorkspace(String email, String password) {
        List<User> users = userRepository.findAllByEmail(email).stream()
                .filter(user -> user.getStatus() != UserStatus.DISABLED)
                .toList();
        if (users.isEmpty()) {
            // Only a candidate can match: their sign-in, unchanged.
            return candidateAuthService.authenticate(email, password)
                    .map(this::issueTokens)
                    .orElseThrow(AuthService::invalidCredentials);
        }

        Instant now = Instant.now();
        List<User> checkable = users.stream().filter(user -> !user.isLocked(now)).toList();
        List<User> matched = checkable.stream()
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .toList();
        CandidateCredentialCheck candidate = candidateAuthService.checkCredentials(email, password);

        if (matched.isEmpty() && !candidate.matched()) {
            checkable.forEach(user -> loginAttemptService.recordFailure(user.getId()));
            if (candidate.outcome() == Outcome.WRONG_PASSWORD) {
                candidateAuthService.recordFailedSignIn(candidate.candidateId());
            }
            throw noMatch(users, checkable, candidate, now);
        }

        if (matched.isEmpty()) {
            return candidateAuthService.completeSignIn(candidate.candidateId())
                    .map(this::issueTokens)
                    .orElseThrow(AuthService::invalidCredentials);
        }
        if (matched.size() == 1 && !candidate.matched()) {
            User user = matched.get(0);
            admit(user);
            return issueTokens(user, resolveTenantName(user.getTenantId()));
        }

        throw workspaceSelection(matched, candidate);
    }

    /**
     * Nothing matched. The generic 401, unless every account is locked: then
     * the earliest unlock is reported, as naming the workspace would.
     */
    private static RuntimeException noMatch(List<User> users, List<User> checkable,
                                            CandidateCredentialCheck candidate, Instant now) {
        if (!checkable.isEmpty() || candidate.outcome() == Outcome.WRONG_PASSWORD) {
            return invalidCredentials();
        }
        // Every company account is locked, and the candidate account is too or does not exist.
        Stream<Instant> locks = users.stream().map(User::getLockedUntil);
        if (candidate.outcome() == Outcome.LOCKED) {
            locks = Stream.concat(locks, Stream.of(candidate.lockedUntil()));
        }
        return new AccountLockedException(locks.min(Comparator.naturalOrder()).orElseThrow(), now);
    }

    /**
     * Records the pending choice and builds the 409 that lists it: company
     * workspaces by name, then the platform account, then the candidate one.
     */
    private WorkspaceSelectionRequiredException workspaceSelection(List<User> matched,
                                                                   CandidateCredentialCheck candidate) {
        Set<UUID> tenantIds = matched.stream()
                .map(User::getTenantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, CompanySummaryResponse> companies = tenantService.findCompanySummaries(tenantIds);

        List<Choice> choices = new ArrayList<>();
        for (User user : matched) {
            CompanySummaryResponse company = user.getTenantId() == null ? null : companies.get(user.getTenantId());
            choices.add(userMapper.toWorkspaceChoice(user, company));
        }
        if (candidate.matched()) {
            choices.add(new Choice(candidate.candidateId(), AccountType.CANDIDATE,
                    null, null, null, CANDIDATE_ROLE_NAME));
        }
        choices.sort(Comparator.comparing(Choice::type)
                .thenComparing(Choice::workspaceName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        String selectionToken = loginSelectionService.issue(choices.stream().map(Choice::accountId).toList());
        log.info("Sign-in credentials matched {} accounts; asking which one", choices.size());
        return new WorkspaceSelectionRequiredException(new WorkspaceSelectionResponse(
                selectionToken, loginSelectionService.ttlSeconds(), List.copyOf(choices)));
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
        requireNotLocked(user, Instant.now());

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw invalidCredentials();
        }

        admit(user);
    }

    private static void requireNotLocked(User user, Instant now) {
        if (user.isLocked(now)) {
            throw new AccountLockedException(user.getLockedUntil(), now);
        }
    }

    /**
     * The gates after a correct password: an account that is not ready yet is
     * refused with the reason, a disabled one generically, and anything else
     * has its failure count cleared.
     */
    private void admit(User user) {
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotVerifiedException(
                    "Your email address is not verified yet. Check your inbox for the "
                            + "verification link, or request a new one.");
        }
        if (user.getStatus() == UserStatus.INVITED) {
            throw AccountNotVerifiedException.invitationPending(
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
        // The role claim is informational: what a company member may do is
        // resolved per request from their current role (RolePermissionResolver).
        String accessToken = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getTenantId(), user.legacyRoleName(), user.getEmail());
        return new AuthResponse(
                accessToken,
                refreshTokenService.issue(user.getId()),
                jwtTokenProvider.getAccessTokenTtl().toSeconds(),
                userMapper.toSessionResponse(user, tenantName));
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
                null,
                CANDIDATE_ROLE_NAME,
                List.of(),
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
