package com.talentpipe.auth.service;

import com.talentpipe.auth.dto.InviteUserRequest;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.entity.InvitationToken;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.InvitationTokenRepository;
import com.talentpipe.auth.repository.RoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.util.SecureTokens;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team invitations (PB-003 HR managers, PB-004 interviewers).
 *
 * <p>Flow: a COMPANY_ADMIN creates an INVITED user in their own tenant with the
 * chosen role → an invitation email goes out with a single-use 7-day link →
 * the invitee sets a password, which flips the account to ACTIVE.</p>
 *
 * <p>Tenant safety: the invitee's tenant is always the inviter's tenant, read
 * from the caller's token. It is never taken from the request, and every lookup
 * in this service is filtered by it, so an admin can neither create users in
 * another tenant nor act on one.</p>
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    /** Roles a COMPANY_ADMIN may grant — never COMPANY_ADMIN or SUPER_ADMIN. */
    private static final Set<RoleName> INVITABLE_ROLES =
            Set.of(RoleName.HR_MANAGER, RoleName.INTERVIEWER);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvitationTokenRepository tokenRepository;
    private final TenantService tenantService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final String frontendBaseUrl;

    public InvitationService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             InvitationTokenRepository tokenRepository,
                             TenantService tenantService,
                             UserMapper userMapper,
                             ApplicationEventPublisher events,
                             org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                             @Value("${talentpipe.app.base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.tenantService = tenantService;
        this.userMapper = userMapper;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Invites a teammate into the caller's tenant.
     *
     * @param tenantId the INVITER's tenant, from their access token
     * @throws AccessDeniedException      if the caller has no tenant (SUPER_ADMIN)
     * @throws BusinessRuleException      if the role is not invitable (422)
     * @throws DuplicateResourceException if that email already exists here (409)
     */
    @Transactional
    public UserResponse invite(UUID tenantId, UUID invitedByUserId, InviteUserRequest request) {
        // Service-layer re-check: the controller's @PreAuthorize proves the
        // ROLE, this proves the SCOPE. A platform admin has no tenant to invite
        // into, so there is no sensible target for the new user.
        if (tenantId == null) {
            throw new AccessDeniedException("Invitations require a tenant-scoped administrator");
        }

        RoleName roleName = RoleName.valueOf(request.role());
        if (!INVITABLE_ROLES.contains(roleName)) {
            throw new BusinessRuleException("Role " + roleName + " cannot be granted by invitation");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new DuplicateResourceException(
                    "A user with email '" + email + "' already exists in this workspace");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " missing from seed data"));

        // A placeholder hash keeps the NOT NULL contract while the account is
        // INVITED. It is unusable: bcrypt never produces this value, so no
        // password can ever match it, and accepting the invite overwrites it.
        User invitee = userRepository.save(new User(
                tenantId,
                role,
                email,
                "!invited",
                request.firstName().trim(),
                request.lastName().trim(),
                UserStatus.INVITED));

        issueAndSend(invitee);

        log.info("User {} invited user {} as {} into tenant {}",
                invitedByUserId, invitee.getId(), roleName, tenantId);
        return userMapper.toResponse(invitee, resolveTenantName(tenantId));
    }

    /**
     * Re-sends an outstanding invitation.
     *
     * @throws ResourceNotFoundException if the user is not an INVITED member of
     *         this tenant — 404 rather than 403, so nothing leaks about users in
     *         other tenants
     */
    @Transactional
    public void resend(UUID tenantId, UUID inviteeUserId) {
        User invitee = userRepository.findById(inviteeUserId)
                .filter(user -> tenantId != null && tenantId.equals(user.getTenantId()))
                .filter(user -> user.getStatus() == UserStatus.INVITED)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        issueAndSend(invitee);
        log.info("Invitation re-sent for user {} in tenant {}", inviteeUserId, tenantId);
    }

    /**
     * Revokes an outstanding invitation, deleting the placeholder account.
     *
     * @throws ResourceNotFoundException if there is no such invitation here (404)
     */
    @Transactional
    public void revoke(UUID tenantId, UUID inviteeUserId) {
        User invitee = userRepository.findById(inviteeUserId)
                .filter(user -> tenantId != null && tenantId.equals(user.getTenantId()))
                .filter(user -> user.getStatus() == UserStatus.INVITED)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        tokenRepository.deleteAllByUserId(invitee.getId());
        userRepository.delete(invitee);
        log.info("Invitation revoked for user {} in tenant {}", inviteeUserId, tenantId);
    }

    /** Everyone in the caller's tenant — members and outstanding invitations. */
    @Transactional(readOnly = true)
    public List<UserResponse> listTeam(UUID tenantId) {
        if (tenantId == null) {
            throw new AccessDeniedException("Team listing requires a tenant-scoped administrator");
        }
        String tenantName = resolveTenantName(tenantId);
        return userRepository.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(user -> userMapper.toResponse(user, tenantName))
                .toList();
    }

    /**
     * Accepts an invitation: sets the first password and activates the account.
     * Public endpoint — the token is the only credential.
     *
     * @throws InvalidTokenException if the token is unknown, expired or used (401)
     */
    @Transactional
    public void accept(String rawToken, String password) {
        InvitationToken token = tokenRepository.findByTokenHash(SecureTokens.sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown or invalid invitation token"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new InvalidTokenException("This invitation has expired - ask your administrator to re-send it");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("This invitation has already been accepted");
        }
        token.markUsed(now);

        User invitee = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("The invited account no longer exists"));

        // Tenant and role come from the invited row — the acceptor cannot
        // influence either.
        invitee.setPasswordHash(passwordEncoder.encode(password));
        invitee.setStatus(UserStatus.ACTIVE);

        log.info("Invitation accepted - user {} activated in tenant {} as {}",
                invitee.getId(), invitee.getTenantId(), invitee.getRole().getName());
    }

    // ------------------------------------------------------------------ util

    private void issueAndSend(User invitee) {
        tokenRepository.deleteAllByUserId(invitee.getId());

        String rawToken = SecureTokens.generate();
        tokenRepository.save(new InvitationToken(
                invitee.getId(), SecureTokens.sha256(rawToken), Instant.now().plus(TOKEN_TTL)));

        events.publishEvent(NotificationRequestedEvent.forUser(
                NotificationType.INVITATION,
                invitee.getTenantId(),
                invitee.getId(),
                invitee.getEmail(),
                frontendBaseUrl + "/accept-invite?token=" + rawToken,
                invitee.getFirstName()));
    }

    private String resolveTenantName(UUID tenantId) {
        return tenantService.findById(tenantId).map(TenantResponse::name).orElse(null);
    }
}
