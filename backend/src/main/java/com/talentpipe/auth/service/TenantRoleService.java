package com.talentpipe.auth.service;

import com.talentpipe.auth.dto.PermissionGroupResponse;
import com.talentpipe.auth.dto.RoleAuditEventResponse;
import com.talentpipe.auth.dto.RoleRequest;
import com.talentpipe.auth.dto.RoleResponse;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleAuditAction;
import com.talentpipe.auth.entity.RoleAuditEvent;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.RoleAuditEventRepository;
import com.talentpipe.auth.repository.RoleRepository;
import com.talentpipe.auth.repository.TenantRoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.security.Permission;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company-defined roles: the use cases behind {@code /api/v1/roles} and the
 * role change on {@code /api/v1/team}.
 *
 * <h2>Rules enforced here, whichever caller arrives</h2>
 * <ol>
 *   <li><strong>Tenant scope.</strong> Every lookup is by id <em>and</em> the
 *       caller's tenant; another company's role or member answers 404.</li>
 *   <li><strong>No privilege escalation.</strong> You can only hand out what
 *       you hold: a role you create, edit, assign or move people into may
 *       contain only permissions you have yourself, and you cannot change the
 *       role of someone whose current role can do more than you can.</li>
 *   <li><strong>Owner is special.</strong> Only an Owner can make someone an
 *       Owner or move an Owner to another role, and the last active Owner can
 *       never be moved — so a workspace can never lock itself out.</li>
 *   <li><strong>Roles in use are not orphaned.</strong> Deleting a role that
 *       people hold requires saying which role they move to.</li>
 *   <li><strong>Every change is audited</strong> in
 *       {@code tenant_role_audit_events}, with who made it and the value
 *       before and after.</li>
 * </ol>
 *
 * <p>Changes take effect on the next request: the permission cache entries of
 * everyone affected are evicted after each change.</p>
 */
@Service
public class TenantRoleService {

    private static final Logger log = LoggerFactory.getLogger(TenantRoleService.class);

    /** Upper bound on roles a workspace may create, so a script cannot fill the table. */
    static final int MAX_CUSTOM_ROLES = 50;

    /** Largest page of audit entries served at once. */
    static final int MAX_AUDIT_PAGE_SIZE = 100;

    private static final String ROLE_NOT_FOUND = "Role not found";

    /** Default roles first, in template order, then custom roles by name. */
    private static final Comparator<TenantRole> DISPLAY_ORDER = Comparator
            .comparing((TenantRole role) -> role.getSystemRole() == null
                    ? SystemRole.values().length
                    : role.getSystemRole().ordinal())
            .thenComparing(role -> role.getName().toLowerCase(Locale.ROOT));
    private static final String MEMBER_NOT_FOUND = "Team member not found";

    private final TenantRoleRepository roleRepository;
    private final RoleRepository legacyRoleRepository;
    private final UserRepository userRepository;
    private final RoleAuditEventRepository auditRepository;
    private final RolePermissionResolver permissionResolver;
    private final HtmlSanitizer htmlSanitizer;
    private final Clock clock;

    public TenantRoleService(TenantRoleRepository roleRepository,
                             RoleRepository legacyRoleRepository,
                             UserRepository userRepository,
                             RoleAuditEventRepository auditRepository,
                             RolePermissionResolver permissionResolver,
                             HtmlSanitizer htmlSanitizer,
                             Clock clock) {
        this.roleRepository = roleRepository;
        this.legacyRoleRepository = legacyRoleRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.permissionResolver = permissionResolver;
        this.htmlSanitizer = htmlSanitizer;
        this.clock = clock;
    }

    // ------------------------------------------------------------ catalogue

    /** The permission catalogue, grouped the way the role editor shows it. */
    public List<PermissionGroupResponse> permissionCatalogue() {
        Map<Permission.Group, List<PermissionGroupResponse.Item>> byGroup = new EnumMap<>(Permission.Group.class);
        for (Permission permission : Permission.values()) {
            byGroup.computeIfAbsent(permission.group(), group -> new ArrayList<>())
                    .add(new PermissionGroupResponse.Item(
                            permission.key(),
                            permission.label(),
                            permission.description(),
                            UserMapper.permissionKeys(permission.requires())));
        }
        return byGroup.entrySet().stream()
                .map(entry -> new PermissionGroupResponse(entry.getKey().name(), entry.getKey().label(),
                        List.copyOf(entry.getValue())))
                .toList();
    }

    // ---------------------------------------------------------- provisioning

    /**
     * Creates a new workspace's three default roles. Participates in the
     * caller's transaction, so a company and its roles are created together.
     */
    @Transactional
    public Map<SystemRole, TenantRole> provisionDefaultRoles(UUID tenantId) {
        Map<SystemRole, TenantRole> roles = new EnumMap<>(SystemRole.class);
        for (SystemRole template : SystemRole.values()) {
            roles.put(template, roleRepository.save(TenantRole.fromTemplate(tenantId, template)));
        }
        return roles;
    }

    /** The transitional fixed role mirroring a company role, or {@code null} for a custom one. */
    public Role legacyRoleFor(TenantRole role) {
        SystemRole template = role.getSystemRole();
        if (template == null) {
            return null;
        }
        return legacyRoleRepository.findByName(template.legacyRole())
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + template.legacyRole() + " missing from seed data"));
    }

    // ---------------------------------------------------------------- reads

    /** The workspace's roles: Owner, Recruiter, Interviewer, then custom roles by name. */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(UUID tenantId) {
        Map<UUID, Long> memberCounts = memberCounts(tenantId);
        return roleRepository.findAllByTenantId(tenantId).stream()
                .sorted(DISPLAY_ORDER)
                .map(role -> toResponse(role, memberCounts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    /** @throws ResourceNotFoundException (404) if missing or another company's */
    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID tenantId, UUID roleId) {
        TenantRole role = requireRole(tenantId, roleId);
        return toResponse(role, userRepository.countByTenantRoleId(roleId));
    }

    /** The workspace's access audit trail, newest first. */
    @Transactional(readOnly = true)
    public Page<RoleAuditEventResponse> auditTrail(UUID tenantId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_AUDIT_PAGE_SIZE));
        return auditRepository.findAllByTenantIdOrderByOccurredAtDesc(tenantId, pageable)
                .map(event -> new RoleAuditEventResponse(
                        event.getId(), event.getAction().name(), event.getActorUserId(), event.getRoleId(),
                        event.getRoleName(), event.getTargetUserId(), event.getPreviousValue(),
                        event.getNewValue(), event.getOccurredAt()));
    }

    // --------------------------------------------------------------- writes

    /**
     * Creates a custom role.
     *
     * @throws InvalidRequestException    (400) a permission key is not in the catalogue,
     *                                    or the name is empty once markup is stripped
     * @throws DuplicateResourceException (409) another role already has this name
     * @throws BusinessRuleException      (422) the workspace has too many roles, or the
     *                                    role would grant something the caller lacks
     */
    @Transactional
    public RoleResponse createRole(UUID tenantId, UUID actorId, RoleRequest request) {
        return doCreateRole(tenantId, actorId, request);
    }

    private RoleResponse doCreateRole(UUID tenantId, UUID actorId, RoleRequest request) {
        if (roleRepository.countByTenantIdAndSystemRoleIsNull(tenantId) >= MAX_CUSTOM_ROLES) {
            throw new BusinessRuleException("A workspace can have at most " + MAX_CUSTOM_ROLES
                    + " custom roles. Delete one you no longer use first.");
        }
        String name = cleanName(request.name());
        if (roleRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw duplicateName(name);
        }
        Set<Permission> permissions = Permission.withRequirements(parsePermissions(request.permissions()));
        requireCanGrant(actorId, permissions);

        TenantRole role = roleRepository.saveAndFlush(
                TenantRole.custom(tenantId, name, cleanDescription(request.description()), permissions));
        audit(tenantId, actorId, RoleAuditAction.ROLE_CREATED, role, null, null, describe(role));
        log.info("Role {} created in tenant {} by {}", role.getId(), tenantId, actorId);
        return toResponse(role, 0);
    }

    /**
     * Replaces a role's name, description and permissions.
     *
     * @throws ResourceNotFoundException (404) missing or another company's
     * @throws InvalidRequestException   (400) no version sent, or an unknown permission
     * @throws ConcurrentUpdateException (409) the version is stale
     * @throws BusinessRuleException     (422) it is the Owner role, or the change
     *                                   involves permissions the caller lacks
     */
    @Transactional
    public RoleResponse updateRole(UUID tenantId, UUID actorId, UUID roleId, RoleRequest request) {
        TenantRole role = requireRole(tenantId, roleId);
        role.requireEditable();
        if (request.version() == null) {
            throw new InvalidRequestException("Send the version of the role you are editing.");
        }
        if (request.version() != role.getVersion()) {
            throw new ConcurrentUpdateException("Someone else changed this role after you opened it. "
                    + "Reload it to see their changes, then edit again.");
        }
        String name = cleanName(request.name());
        if (roleRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, roleId)) {
            throw duplicateName(name);
        }
        Set<Permission> permissions = Permission.withRequirements(parsePermissions(request.permissions()));
        // Both what the role can do now and what it would be able to do: you
        // may neither strip nor grant powers you do not hold yourself.
        Set<Permission> touched = EnumSet.noneOf(Permission.class);
        touched.addAll(permissions);
        touched.addAll(role.permissions());
        requireCanGrant(actorId, touched);

        String before = describe(role);
        role.update(name, cleanDescription(request.description()), permissions);
        TenantRole saved = roleRepository.saveAndFlush(role);
        String after = describe(saved);
        if (!before.equals(after)) {
            audit(tenantId, actorId, RoleAuditAction.ROLE_UPDATED, saved, null, before, after);
        }
        permissionResolver.evictRole(roleId);
        return toResponse(saved, userRepository.countByTenantRoleId(roleId));
    }

    /**
     * Creates a custom role with the same permissions as an existing one,
     * named "… (copy)" — the quickest way to start a role from a default one.
     */
    @Transactional
    public RoleResponse duplicateRole(UUID tenantId, UUID actorId, UUID roleId) {
        TenantRole source = requireRole(tenantId, roleId);
        RoleRequest copy = new RoleRequest(
                uniqueCopyName(tenantId, source.getName()),
                source.getDescription(),
                UserMapper.permissionKeys(source.permissions()),
                null);
        return doCreateRole(tenantId, actorId, copy);
    }

    /**
     * Deletes a custom role. People who hold it are moved to {@code reassignTo}
     * first, in the same transaction.
     *
     * @throws BusinessRuleException (422) it is a default role; people hold it
     *         and no {@code reassignTo} was given; or the move is not allowed
     */
    @Transactional
    public void deleteRole(UUID tenantId, UUID actorId, UUID roleId, UUID reassignTo) {
        TenantRole role = requireRole(tenantId, roleId);
        if (role.getSystemRole() != null) {
            throw new BusinessRuleException("Default roles can't be deleted. You can rename and edit them instead.");
        }
        requireCanGrant(actorId, role.permissions());

        List<User> members = userRepository.findAllByTenantRoleId(roleId);
        if (!members.isEmpty()) {
            if (reassignTo == null) {
                throw new BusinessRuleException(members.size() + (members.size() == 1 ? " person has" : " people have")
                        + " this role. Choose a role to move them to before deleting it.");
            }
            if (reassignTo.equals(roleId)) {
                throw new BusinessRuleException("Choose a different role to move its members to.");
            }
            TenantRole target = requireRole(tenantId, reassignTo);
            requireCanAssign(actorId, target);
            Role legacy = legacyRoleFor(target);
            for (User member : members) {
                member.assignRole(target, legacy);
                audit(tenantId, actorId, RoleAuditAction.MEMBER_ROLE_CHANGED, target, member.getId(),
                        role.getName(), target.getName());
                permissionResolver.evictUser(member.getId());
            }
            userRepository.saveAllAndFlush(members);
        }

        audit(tenantId, actorId, RoleAuditAction.ROLE_DELETED, role, null, describe(role),
                members.isEmpty() ? null : members.size() + " member(s) moved");
        roleRepository.delete(role);
        roleRepository.flush();
        permissionResolver.evictRole(roleId);
        log.info("Role {} deleted in tenant {} by {} ({} member(s) moved)", roleId, tenantId, actorId, members.size());
    }

    /**
     * Moves a member — or a pending invitee — to another role.
     *
     * @throws ResourceNotFoundException (404) member or role missing, or another company's
     * @throws BusinessRuleException     (422) the change would remove the last
     *         Owner, involves Owner without the caller being one, or involves
     *         permissions the caller lacks
     */
    @Transactional
    public User changeMemberRole(UUID tenantId, UUID actorId, UUID memberId, UUID roleId) {
        User member = userRepository.findById(memberId)
                .filter(user -> tenantId.equals(user.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(MEMBER_NOT_FOUND));
        TenantRole target = requireRole(tenantId, roleId);
        TenantRole current = member.getTenantRole();
        if (current != null && current.getId().equals(target.getId())) {
            return member;
        }

        // You may not change the role of someone who can do more than you can.
        if (current != null) {
            requireCanAssign(actorId, current);
        }
        requireCanAssign(actorId, target);

        if (current != null && current.isOwner() && member.getStatus() == UserStatus.ACTIVE
                && userRepository.countByTenantIdAndTenantRole_SystemRoleAndStatus(
                        tenantId, SystemRole.OWNER, UserStatus.ACTIVE) <= 1) {
            throw new BusinessRuleException("Every workspace needs at least one Owner. "
                    + "Make someone else an Owner before changing this person's role.");
        }

        member.assignRole(target, legacyRoleFor(target));
        User saved = userRepository.saveAndFlush(member);
        audit(tenantId, actorId, RoleAuditAction.MEMBER_ROLE_CHANGED, target, memberId,
                current == null ? null : current.getName(), target.getName());
        permissionResolver.evictUser(memberId);
        log.info("User {} moved to role {} in tenant {} by {}", memberId, roleId, tenantId, actorId);
        return saved;
    }

    /**
     * The role an invitation adds someone to, after checking the inviter may
     * hand it out. Invitations cannot make someone an Owner: they join as
     * another role and can be promoted once they have accepted.
     *
     * @param roleId     the role chosen, or {@code null} to use {@code legacyRole}
     * @param legacyRole the old way of choosing: {@code HR_MANAGER} or {@code INTERVIEWER}
     * @throws InvalidRequestException   (400) neither was given
     * @throws ResourceNotFoundException (404) the role is not one of this workspace's
     * @throws BusinessRuleException     (422) it is the Owner role, or grants more
     *                                   than the inviter holds
     */
    @Transactional(readOnly = true)
    public TenantRole roleForInvitation(UUID tenantId, UUID inviterId, UUID roleId, String legacyRole) {
        TenantRole role;
        if (roleId != null) {
            role = requireRole(tenantId, roleId);
        } else if (legacyRole != null) {
            SystemRole template = "HR_MANAGER".equals(legacyRole) ? SystemRole.RECRUITER : SystemRole.INTERVIEWER;
            role = roleRepository.findByTenantIdAndSystemRole(tenantId, template)
                    .orElseThrow(() -> new ResourceNotFoundException(ROLE_NOT_FOUND));
        } else {
            throw new InvalidRequestException("Choose a role for the new member.");
        }
        if (role.isOwner()) {
            throw new BusinessRuleException("People can't be invited as Owner. Invite them with another role "
                    + "and make them an Owner once they have joined.");
        }
        requireCanGrant(inviterId, role.permissions());
        return role;
    }

    // -------------------------------------------------------------- helpers

    private TenantRole requireRole(UUID tenantId, UUID roleId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ROLE_NOT_FOUND));
    }

    /** @throws BusinessRuleException (422) if the actor lacks any of {@code permissions} */
    private void requireCanGrant(UUID actorId, Set<Permission> permissions) {
        Set<Permission> held = permissionResolver.currentPermissions(actorId);
        List<String> missing = Arrays.stream(Permission.values())
                .filter(permissions::contains)
                .filter(permission -> !held.contains(permission))
                .map(Permission::label)
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("You can only give out permissions you have yourself. "
                    + "You don't have: " + String.join(", ", missing) + ".");
        }
    }

    /** Assigning or leaving {@code role}: Owner needs an Owner; anything else, its permissions. */
    private void requireCanAssign(UUID actorId, TenantRole role) {
        if (role.isOwner() && !isOwner(actorId)) {
            throw new BusinessRuleException("Only an Owner can make someone an Owner or change an Owner's role.");
        }
        requireCanGrant(actorId, role.permissions());
    }

    private boolean isOwner(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getTenantRole)
                .map(TenantRole::isOwner)
                .orElse(false);
    }

    private Set<Permission> parsePermissions(List<String> keys) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String key : keys) {
            permissions.add(Permission.fromKey(key).orElseThrow(() ->
                    new InvalidRequestException("Unknown permission '" + key + "'. "
                            + "Use the keys listed by GET /api/v1/permissions.")));
        }
        return permissions;
    }

    private String cleanName(String raw) {
        String name = htmlSanitizer.sanitize(raw == null ? "" : raw).trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new InvalidRequestException("Give the role a name.");
        }
        return name;
    }

    private String cleanDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String description = htmlSanitizer.sanitize(raw).trim();
        return description.isEmpty() ? null : description;
    }

    private String uniqueCopyName(UUID tenantId, String sourceName) {
        String base = sourceName.length() > TenantRole.MAX_NAME_LENGTH - 10
                ? sourceName.substring(0, TenantRole.MAX_NAME_LENGTH - 10).stripTrailing()
                : sourceName;
        String candidate = base + " (copy)";
        for (int n = 2; roleRepository.existsByTenantIdAndNameIgnoreCase(tenantId, candidate); n++) {
            candidate = base + " (copy " + n + ")";
        }
        return candidate;
    }

    private static DuplicateResourceException duplicateName(String name) {
        return new DuplicateResourceException("There is already a role called '" + name + "'. Choose another name.");
    }

    private Map<UUID, Long> memberCounts(UUID tenantId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : userRepository.countMembersByRole(tenantId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private RoleResponse toResponse(TenantRole role, long memberCount) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getSystemRole() == null ? null : role.getSystemRole().name(),
                role.isLocked(),
                role.getSystemRole() == null,
                UserMapper.permissionKeys(role.permissions()),
                memberCount,
                role.getVersion(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    /** "Name: permission, permission" — the readable value the audit trail stores. */
    private static String describe(TenantRole role) {
        String permissions = UserMapper.permissionKeys(role.permissions()).stream().collect(Collectors.joining(", "));
        return role.getName() + ": " + (permissions.isEmpty() ? "(no permissions)" : permissions);
    }

    private void audit(UUID tenantId, UUID actorId, RoleAuditAction action, TenantRole role, UUID targetUserId,
                       String previousValue, String newValue) {
        auditRepository.save(new RoleAuditEvent(tenantId, actorId, action, role.getId(), role.getName(),
                targetUserId, previousValue, newValue, clock.instant()));
    }
}
