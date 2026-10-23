package com.talentpipe.testsupport;

import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authorities a request would carry in production, for tests that build
 * their own security context (controller slices have no
 * {@code JwtAuthenticationFilter} and no {@code RolePermissionResolver}).
 *
 * <p>Built from the same {@link SystemRole} templates production seeds, so a
 * slice test that says "as an HR manager" exercises exactly the permissions a
 * real Recruiter has — and starts failing if the template changes in a way
 * that matters.</p>
 */
public final class TestAuthorities {

    private TestAuthorities() {
        // static helpers only
    }

    /**
     * What a member of a default role, or a candidate or platform operator,
     * would hold.
     *
     * @param role an old fixed role name: {@code COMPANY_ADMIN},
     *             {@code HR_MANAGER}, {@code INTERVIEWER}, {@code CANDIDATE}
     *             or {@code SUPER_ADMIN}
     */
    public static List<GrantedAuthority> forRole(String role) {
        RoleName roleName = RoleName.valueOf(role);
        return SystemRole.forLegacyRole(roleName)
                .map(template -> companyMember(template.defaultPermissions().toArray(Permission[]::new), roleName))
                .orElseGet(() -> List.of(new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + role)));
    }

    /** What a member of a custom role with exactly these permissions would hold. */
    public static List<GrantedAuthority> withPermissions(Permission... permissions) {
        return companyMember(permissions, null);
    }

    private static List<GrantedAuthority> companyMember(Permission[] permissions, RoleName legacyRole) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Authorities.COMPANY_MEMBER));
        Arrays.stream(permissions).map(Permission::key).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        if (legacyRole != null) {
            authorities.add(new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + legacyRole.name()));
        }
        return authorities;
    }
}
