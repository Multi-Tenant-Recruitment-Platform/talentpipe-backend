package com.talentpipe.auth.controller;

import com.talentpipe.auth.dto.InviteUserRequest;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.service.InvitationService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team management for company administrators (PB-002, PB-003, PB-004).
 *
 * <p>Authorization is enforced twice, on purpose. {@code @PreAuthorize} here
 * rejects the wrong ROLE with 403 before any work happens; {@link InvitationService}
 * independently re-checks the tenant SCOPE, so a future caller that reaches the
 * service another way cannot bypass it. Neither check trusts the request body.</p>
 *
 * <p>The tenant always comes from the caller's access token. Cross-tenant
 * targets answer 404, never 403 — existence must not leak across tenants.</p>
 */
@RestController
@RequestMapping("/api/v1/team")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class TeamController {

    private final InvitationService invitationService;

    public TeamController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /** Everyone in the caller's workspace: active members and pending invitations. */
    @GetMapping
    public List<UserResponse> listTeam(@AuthenticationPrincipal UserPrincipal principal) {
        return invitationService.listTeam(principal.tenantId());
    }

    /** Invites an HR manager or interviewer into the caller's tenant. */
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse invite(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody InviteUserRequest request) {
        return invitationService.invite(principal.tenantId(), principal.id(), request);
    }

    /** Re-sends an outstanding invitation email. */
    @PostMapping("/invitations/{userId}/resend")
    public ResponseEntity<Void> resend(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID userId) {
        invitationService.resend(principal.tenantId(), userId);
        return ResponseEntity.noContent().build();
    }

    /** Revokes an outstanding invitation and removes the placeholder account. */
    @DeleteMapping("/invitations/{userId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID userId) {
        invitationService.revoke(principal.tenantId(), userId);
        return ResponseEntity.noContent().build();
    }
}
