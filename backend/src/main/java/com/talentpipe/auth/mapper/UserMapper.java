package com.talentpipe.auth.mapper;

import com.talentpipe.auth.dto.TeamMemberResponse;
import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.AccountType;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.Choice;
import com.talentpipe.auth.entity.InvitationToken;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.security.Permission;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Entity → DTO mapping for the auth module. The password hash is dropped here
 * by construction: no response type has a field that could carry it.
 */
@Component
public class UserMapper {

    /** What a platform operator's role is called, since they hold no company role. */
    static final String PLATFORM_ROLE_NAME = "Platform administrator";

    /**
     * A user as they appear inside another response — no permission list.
     *
     * @param tenantName display name of the user's tenant, resolved by the
     *                   caller via {@code TenantService} ({@code null} for
     *                   SUPER_ADMIN)
     */
    public UserResponse toResponse(User user, String tenantName) {
        return toUserResponse(user, tenantName, null);
    }

    /**
     * The signed-in user, with the permissions their role grants — returned
     * by login, refresh and {@code /me}, so the SPA knows what to show.
     */
    public UserResponse toSessionResponse(User user, String tenantName) {
        TenantRole role = user.getTenantRole();
        List<String> permissions = role == null ? List.of() : permissionKeys(role.permissions());
        return toUserResponse(user, tenantName, permissions);
    }

    /**
     * One row of the team screen.
     *
     * @param invitation the member's outstanding invitation, or {@code null}
     *                   when they have accepted (or none was ever sent)
     */
    public TeamMemberResponse toTeamMember(User user, String tenantName, InvitationToken invitation) {
        TenantRole role = user.getTenantRole();
        return new TeamMemberResponse(
                user.getId(),
                user.getTenantId(),
                tenantName,
                user.legacyRoleName(),
                role == null ? null : role.getId(),
                roleName(user),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.getCreatedAt(),
                invitation == null ? null : invitation.getCreatedAt(),
                invitation == null ? null : invitation.getExpiresAt());
    }

    /**
     * One account offered when a sign-in's credentials fit several.
     *
     * @param company the user's company; {@code null} for a platform administrator
     */
    public Choice toWorkspaceChoice(User user, CompanySummaryResponse company) {
        if (user.getTenantId() == null) {
            return new Choice(user.getId(), AccountType.PLATFORM, null, null, null, roleName(user));
        }
        return new Choice(
                user.getId(),
                AccountType.COMPANY,
                company == null ? null : company.name(),
                company == null ? null : company.subdomain(),
                company == null ? null : company.logoUrl(),
                roleName(user));
    }

    /** Permission keys in catalogue order, so every response lists them the same way. */
    public static List<String> permissionKeys(Set<Permission> permissions) {
        return Arrays.stream(Permission.values())
                .filter(permissions::contains)
                .map(Permission::key)
                .toList();
    }

    private UserResponse toUserResponse(User user, String tenantName, List<String> permissions) {
        TenantRole role = user.getTenantRole();
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                tenantName,
                user.legacyRoleName(),
                role == null ? null : role.getId(),
                roleName(user),
                permissions,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.getCreatedAt());
    }

    private static String roleName(User user) {
        return user.getTenantRole() != null ? user.getTenantRole().getName() : PLATFORM_ROLE_NAME;
    }
}
