package com.talentpipe.security;

import org.springframework.security.core.Authentication;

/**
 * The non-permission authorities a request can carry, next to the permission
 * keys of {@link Permission}.
 *
 * <ul>
 *   <li>{@link #COMPANY_MEMBER} — every active company user, whatever their
 *       role. Guards what anyone in the workspace may do: read the company
 *       profile, see the vacancies they are assigned to.</li>
 *   <li>{@code ROLE_CANDIDATE} / {@code ROLE_SUPER_ADMIN} — the two identities
 *       that have no company role. Checked with {@code hasRole(...)}.</li>
 *   <li>{@code ROLE_COMPANY_ADMIN} / {@code ROLE_HR_MANAGER} /
 *       {@code ROLE_INTERVIEWER} — <strong>transitional</strong>. Granted only
 *       to members of the three default roles, so feature branches written
 *       against the old fixed roles keep working until they move to
 *       {@link RequirePermission}. A member of a custom role does not get
 *       them, so old-style checks fail closed for them.
 *       TODO(sprint3): remove once no {@code hasRole} check on a company
 *       endpoint remains (see the mapping table in docs/DECISIONS.md).</li>
 * </ul>
 */
public final class Authorities {

    /** Held by every active company user, in addition to their role's permissions. */
    public static final String COMPANY_MEMBER = "COMPANY_MEMBER";

    /** Spring's role prefix: {@code hasRole('X')} checks the authority {@code ROLE_X}. */
    public static final String ROLE_PREFIX = "ROLE_";

    private Authorities() {
        // constants only
    }

    /**
     * Whether the authenticated caller currently holds a permission — for the
     * rare endpoint whose answer, not just its access, depends on one.
     */
    public static boolean has(Authentication authentication, Permission permission) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.key().equals(authority.getAuthority()));
    }
}
