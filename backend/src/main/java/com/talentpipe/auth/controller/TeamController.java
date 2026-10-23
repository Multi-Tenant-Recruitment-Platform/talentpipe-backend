package com.talentpipe.auth.controller;

import com.talentpipe.auth.dto.ChangeMemberRoleRequest;
import com.talentpipe.auth.dto.InviteUserRequest;
import com.talentpipe.auth.dto.TeamMemberResponse;
import com.talentpipe.auth.service.InvitationService;
import com.talentpipe.security.Permission;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team management (PB-002, PB-003, PB-004): who is in the workspace, inviting
 * people, and which role each person holds.
 *
 * <p>Authorization is enforced twice, on purpose. {@code @RequirePermission}
 * here rejects a caller whose role lacks the permission with 403 before any
 * work happens; the services independently scope every lookup to the
 * caller's TENANT and refuse to hand out more than the caller holds, so a
 * future caller that reaches them another way cannot bypass either check.
 * Neither check trusts the request body.</p>
 *
 * <p>The tenant always comes from the caller's access token. Cross-tenant
 * targets answer 404, never 403 — existence must not leak across tenants.</p>
 */
@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final InvitationService invitationService;

    public TeamController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Everyone in the caller's workspace: active members and pending invitations, with their roles. */
    @GetMapping
    @RequirePermission(Permission.Keys.TEAM_VIEW)
    public List<TeamMemberResponse> listTeam(@AuthenticationPrincipal UserPrincipal principal) {
        return invitationService.listTeam(principal.tenantId());
    }

    /** Invites someone into the caller's workspace with one of its roles (not Owner). */
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permission.Keys.TEAM_INVITE)
    public TeamMemberResponse invite(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody InviteUserRequest request) {
        return invitationService.invite(principal.tenantId(), principal.id(), request);
    }

    /** Re-sends an outstanding invitation email. */
    @PostMapping("/invitations/{userId}/resend")
    @RequirePermission(Permission.Keys.TEAM_INVITE)
    public ResponseEntity<Void> resend(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID userId) {
        invitationService.resend(principal.tenantId(), userId);
        return ResponseEntity.noContent().build();
    }

    /** Revokes an outstanding invitation and removes the placeholder account. */
    @DeleteMapping("/invitations/{userId}")
    @RequirePermission(Permission.Keys.TEAM_INVITE)
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID userId) {
        invitationService.revoke(principal.tenantId(), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Moves a member — or a pending invitee — to another role. 422 if it
     * would leave the workspace without an Owner, or involves permissions the
     * caller does not have.
     */
    @PutMapping("/members/{userId}/role")
    @RequirePermission(Permission.Keys.TEAM_MANAGE)
    public TeamMemberResponse changeRole(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID userId,
                                         @Valid @RequestBody ChangeMemberRoleRequest request) {
        return invitationService.changeRole(principal.tenantId(), principal.id(), userId, request.roleId());
    }
}
