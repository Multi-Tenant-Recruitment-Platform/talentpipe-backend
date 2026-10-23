package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import com.talentpipe.security.PermissionResolver;
import com.talentpipe.security.UserPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves what each request may do from the caller's <em>current</em> role.
 *
 * <p>The access token deliberately does not carry permissions. If it did, an
 * admin who removed a permission from a role — or moved someone to a weaker
 * role — would have to wait up to the token's 15-minute lifetime for the
 * change to bite. Instead each request looks the member's role up, through a
 * small cache:</p>
 * <ul>
 *   <li>{@link TenantRoleService} evicts the affected entries the moment a
 *       role is edited or deleted, or a member changes role, so on a single
 *       instance a change applies to the very next request;</li>
 *   <li>entries also expire after {@code talentpipe.security.permission-cache-ttl}
 *       (default 60 s), which bounds how stale another instance can be once
 *       the backend runs more than one replica.</li>
 * </ul>
 *
 * <p>Candidates and platform operators have no company role; their authority
 * is their role claim, exactly as before.</p>
 */
@Service
public class RolePermissionResolver implements PermissionResolver {

    /** Past this many cached members the cache is simply dropped; it refills on demand. */
    static final int MAX_ENTRIES = 10_000;

    private final UserRepository userRepository;
    private final Clock clock;
    private final Duration ttl;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public RolePermissionResolver(UserRepository userRepository, Clock clock,
                                  @Value("${talentpipe.security.permission-cache-ttl:60s}") Duration ttl) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public Set<GrantedAuthority> authoritiesFor(UserPrincipal principal) {
        if (principal.tenantId() == null) {
            // Candidate or platform operator: the role claim is the whole story.
            return Set.of(new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + principal.role()));
        }
        Instant now = clock.instant();
        Entry cached = cache.get(principal.id());
        if (cached != null && cached.isFresh(now, ttl) && principal.tenantId().equals(cached.tenantId())) {
            return cached.authorities();
        }
        Entry loaded = load(principal.id(), principal.tenantId(), now);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(principal.id(), loaded);
        return loaded.authorities();
    }

    /**
     * The permissions a member holds right now, read from the database — for
     * decisions that must not trust a cache, such as whether someone may grant
     * a permission to somebody else.
     *
     * @return empty for an unknown, inactive or role-less user
     */
    @Transactional(readOnly = true)
    public Set<Permission> currentPermissions(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(User::getTenantRole)
                .map(TenantRole::permissions)
                .orElse(Set.of());
    }

    // ------------------------------------------------------------- eviction

    /** Forget one member — after they were moved to another role. */
    public void evictUser(UUID userId) {
        cache.remove(userId);
    }

    /** Forget every member of a role — after the role was edited or deleted. */
    public void evictRole(UUID roleId) {
        cache.values().removeIf(entry -> roleId.equals(entry.roleId()));
    }

    // ---------------------------------------------------------------- loading

    private Entry load(UUID userId, UUID tokenTenantId, Instant now) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()
                || !tokenTenantId.equals(found.get().getTenantId())
                || found.get().getStatus() != UserStatus.ACTIVE
                || found.get().getTenantRole() == null) {
            // Gone, moved, deactivated: authenticated, but allowed nothing.
            return new Entry(tokenTenantId, null, Set.of(), now);
        }
        User user = found.get();
        TenantRole role = user.getTenantRole();

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(Authorities.COMPANY_MEMBER));
        role.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.key())));
        // Transitional: members of the default roles keep the old ROLE_* authority
        // so branches still using hasRole(...) work until they move to permissions.
        if (user.getRole() != null && user.getRole().getName() != RoleName.SUPER_ADMIN) {
            authorities.add(new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + user.getRole().getName().name()));
        }
        return new Entry(user.getTenantId(), role.getId(), Set.copyOf(authorities), now);
    }

    private record Entry(UUID tenantId, UUID roleId, Set<GrantedAuthority> authorities, Instant loadedAt) {

        boolean isFresh(Instant now, Duration ttl) {
            return loadedAt.plus(ttl).isAfter(now);
        }
    }
}
