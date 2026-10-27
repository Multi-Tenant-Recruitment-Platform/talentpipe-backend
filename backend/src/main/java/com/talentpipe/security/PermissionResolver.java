package com.talentpipe.security;

import java.util.Set;
import org.springframework.security.core.GrantedAuthority;

/**
 * Works out what an authenticated caller may do, from their verified token
 * claims.
 *
 * <p>Implemented in the auth module, which owns users and their roles; the
 * security layer only depends on this interface, so it does not reach into
 * another module's repositories.</p>
 *
 * <p>Called on every authenticated request, so implementations must be cheap
 * (cached). They must also be current: a permission removed from a role, or a
 * member moved to another role, has to stop working on the next request, not
 * when the access token happens to expire.</p>
 */
public interface PermissionResolver {

    /**
     * @return the authorities for this request: for a company user,
     *         {@link Authorities#COMPANY_MEMBER} plus their role's permission
     *         keys; for a candidate or platform operator, their role. An
     *         account that no longer exists or is no longer active gets none.
     */
    Set<GrantedAuthority> authoritiesFor(UserPrincipal principal);
}
