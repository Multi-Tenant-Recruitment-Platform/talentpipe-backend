package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.security.Permission;
import com.talentpipe.security.UserPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link RolePermissionResolver}: what each kind of caller is
 * allowed, and — the reason it exists — that a change to someone's role is
 * picked up on the next request.
 */
class RolePermissionResolverTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private MutableClock clock;
    private RolePermissionResolver resolver;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        resolver = new RolePermissionResolver(userRepository, clock, Duration.ofSeconds(60));
    }

    // ------------------------------------------------------- non-company users

    @Test
    void candidate_getsTheirRole_withoutTouchingTheDatabase() {
        Set<String> authorities = names(resolver.authoritiesFor(
                new UserPrincipal(UUID.randomUUID(), null, "CANDIDATE", "c@example.com")));

        assertThat(authorities).containsExactly("ROLE_CANDIDATE");
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformOperator_getsTheirRole() {
        Set<String> authorities = names(resolver.authoritiesFor(
                new UserPrincipal(UUID.randomUUID(), null, "SUPER_ADMIN", "ops@talentpipe.io")));

        assertThat(authorities).containsExactly("ROLE_SUPER_ADMIN");
    }

    // ------------------------------------------------------- company members

    @Test
    void memberOfADefaultRole_getsMembership_permissions_andTheTransitionalLegacyRole() {
        User recruiter = member(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER), RoleName.HR_MANAGER);

        Set<String> authorities = names(resolver.authoritiesFor(principalOf(recruiter)));

        assertThat(authorities).contains("COMPANY_MEMBER", "jobs.manage", "jobs.publish", "ROLE_HR_MANAGER")
                .doesNotContain("team.view", "settings.edit");
    }

    @Test
    void memberOfACustomRole_getsItsPermissions_andNoLegacyRole() {
        User coordinator = member(TenantRole.custom(TENANT, "Coordinator", null,
                EnumSet.of(Permission.INTERVIEWS_SCHEDULE)), null);

        Set<String> authorities = names(resolver.authoritiesFor(principalOf(coordinator)));

        assertThat(authorities).containsExactlyInAnyOrder("COMPANY_MEMBER", "interviews.schedule");
    }

    @Test
    void owner_getsEveryPermission() {
        User owner = member(TenantRole.fromTemplate(TENANT, SystemRole.OWNER), RoleName.COMPANY_ADMIN);

        Set<String> authorities = names(resolver.authoritiesFor(principalOf(owner)));

        for (Permission permission : Permission.values()) {
            assertThat(authorities).contains(permission.key());
        }
    }

    @Test
    void inactiveMember_isAuthenticatedButAllowedNothing() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER), RoleName.HR_MANAGER);
        member.setStatus(UserStatus.DISABLED);

        assertThat(resolver.authoritiesFor(principalOf(member))).isEmpty();
    }

    @Test
    void tokenForAnotherTenantThanTheUsersOwn_isAllowedNothing() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.OWNER), RoleName.COMPANY_ADMIN);

        assertThat(resolver.authoritiesFor(
                new UserPrincipal(member.getId(), UUID.randomUUID(), "COMPANY_ADMIN", "a@b.io"))).isEmpty();
    }

    @Test
    void deletedUser_isAllowedNothing() {
        UUID ghost = UUID.randomUUID();
        when(userRepository.findById(ghost)).thenReturn(Optional.empty());

        assertThat(resolver.authoritiesFor(new UserPrincipal(ghost, TENANT, "HR_MANAGER", "g@b.io"))).isEmpty();
    }

    // ----------------------------------------------------------------- caching

    @Test
    void repeatRequests_areServedFromTheCache() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER), RoleName.HR_MANAGER);

        resolver.authoritiesFor(principalOf(member));
        resolver.authoritiesFor(principalOf(member));

        verify(userRepository, times(1)).findById(member.getId());
    }

    @Test
    void evictingAUser_makesTheNextRequestSeeTheirNewRole() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER), RoleName.HR_MANAGER);
        resolver.authoritiesFor(principalOf(member));

        member.assignRole(TenantRole.custom(TENANT, "Observer", null, EnumSet.noneOf(Permission.class)), null);
        resolver.evictUser(member.getId());

        assertThat(names(resolver.authoritiesFor(principalOf(member)))).containsExactly("COMPANY_MEMBER");
    }

    @Test
    void evictingARole_dropsEveryMemberOfIt() {
        TenantRole role = persisted(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        User member = member(role, RoleName.HR_MANAGER);
        resolver.authoritiesFor(principalOf(member));

        role.update("Recruiter", null, EnumSet.of(Permission.JOBS_VIEW_ALL));
        resolver.evictRole(role.getId());

        assertThat(names(resolver.authoritiesFor(principalOf(member))))
                .contains("jobs.view.all").doesNotContain("jobs.manage");
    }

    @Test
    void entries_expireAfterTheTtl_soAnotherInstancesChangeIsPickedUpEventually() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER), RoleName.HR_MANAGER);
        resolver.authoritiesFor(principalOf(member));

        clock.advance(Duration.ofSeconds(61));
        resolver.authoritiesFor(principalOf(member));

        verify(userRepository, times(2)).findById(member.getId());
    }

    @Test
    void currentPermissions_readsTheDatabase_andIgnoresInactiveUsers() {
        User member = member(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER), RoleName.INTERVIEWER);
        assertThat(resolver.currentPermissions(member.getId())).containsExactly(Permission.INTERVIEWS_CONDUCT);

        member.setStatus(UserStatus.INVITED);
        assertThat(resolver.currentPermissions(member.getId())).isEmpty();
    }

    // ----------------------------------------------------------------- helpers

    private User member(TenantRole role, RoleName legacy) {
        Role legacyRole = null;
        if (legacy != null) {
            legacyRole = mock(Role.class);
            when(legacyRole.getName()).thenReturn(legacy);
        }
        User user = new User(TENANT, persisted(role), legacyRole, "m@acme.io", "hash", "Ada", "Lovelace",
                UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static TenantRole persisted(TenantRole role) {
        if (role.getId() == null) {
            ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        }
        return role;
    }

    private static UserPrincipal principalOf(User user) {
        return new UserPrincipal(user.getId(), user.getTenantId(), user.legacyRoleName(), user.getEmail());
    }

    private static Set<String> names(Set<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
