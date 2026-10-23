package com.talentpipe.auth.controller;

import com.talentpipe.auth.dto.PermissionGroupResponse;
import com.talentpipe.auth.dto.RoleAuditEventResponse;
import com.talentpipe.auth.dto.RoleRequest;
import com.talentpipe.auth.dto.RoleResponse;
import com.talentpipe.auth.service.TenantRoleService;
import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.security.Permission;
import com.talentpipe.security.RequireCompanyMember;
import com.talentpipe.security.RequirePermission;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company-defined roles ("company-role permission APIs").
 *
 * <p>A company builds its own roles out of the platform's permission
 * catalogue — a "Hiring Coordinator" who manages the team and organises
 * interviews, a "Panel Interviewer" who only conducts them — and gives each
 * member one role on the team screen.</p>
 *
 * <ul>
 *   <li>{@code GET /api/v1/permissions} — the catalogue, for the role editor;</li>
 *   <li>{@code /api/v1/roles} — the workspace's roles;</li>
 *   <li>{@code PUT /api/v1/team/members/{id}/role} (on {@link TeamController})
 *       — moving a member to a role.</li>
 * </ul>
 *
 * <p>Reading roles needs {@code team.view} (inviting someone means picking
 * one); changing them needs {@code roles.manage}. The rules that keep this
 * safe — no escalation, the last Owner, reassigning before delete — are
 * enforced by {@link TenantRoleService}. Another company's role answers 404.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final TenantRoleService roleService;

    public RoleController(TenantRoleService roleService) {
        this.roleService = roleService;
    }

    /** The permission catalogue, grouped as the role editor shows it. */
    @GetMapping("/permissions")
    @RequireCompanyMember
    public List<PermissionGroupResponse> permissions() {
        return roleService.permissionCatalogue();
    }

    /** The workspace's roles: Owner, Recruiter, Interviewer, then custom roles by name. */
    @GetMapping("/roles")
    @RequirePermission(Permission.Keys.TEAM_VIEW)
    public List<RoleResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return roleService.listRoles(principal.tenantId());
    }

    @GetMapping("/roles/{id}")
    @RequirePermission(Permission.Keys.TEAM_VIEW)
    public RoleResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return roleService.getRole(principal.tenantId(), id);
    }

    /** Creates a custom role. 409 if the name is taken; 422 if it grants more than the caller holds. */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permission.Keys.ROLES_MANAGE)
    public RoleResponse create(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody RoleRequest request) {
        return roleService.createRole(principal.tenantId(), principal.id(), request);
    }

    /** Replaces a role's name, description and permissions. 409 on a stale {@code version}; 422 for Owner. */
    @PutMapping("/roles/{id}")
    @RequirePermission(Permission.Keys.ROLES_MANAGE)
    public RoleResponse update(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID id,
                               @Valid @RequestBody RoleRequest request) {
        return roleService.updateRole(principal.tenantId(), principal.id(), id, request);
    }

    /** Creates a custom copy of a role — the quickest way to start from a default one. */
    @PostMapping("/roles/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permission.Keys.ROLES_MANAGE)
    public RoleResponse duplicate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return roleService.duplicateRole(principal.tenantId(), principal.id(), id);
    }

    /**
     * Deletes a custom role. If people hold it, {@code reassignTo} names the
     * role they move to; without it the request answers 422.
     */
    @DeleteMapping("/roles/{id}")
    @RequirePermission(Permission.Keys.ROLES_MANAGE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID id,
                                       @RequestParam(required = false) UUID reassignTo) {
        roleService.deleteRole(principal.tenantId(), principal.id(), id, reassignTo);
        return ResponseEntity.noContent().build();
    }

    /** Who changed which role, and who was moved between roles — newest first. */
    @GetMapping("/roles/audit")
    @RequirePermission(Permission.Keys.ROLES_MANAGE)
    public PageResponse<RoleAuditEventResponse> audit(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(roleService.auditTrail(principal.tenantId(), page, size));
    }
}
