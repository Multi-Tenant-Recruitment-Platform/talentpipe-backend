package com.talentpipe.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.security.Permission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link TenantRole} and the {@link SystemRole} templates it is created from. */
class TenantRoleTest {

    private static final UUID TENANT = UUID.randomUUID();

    // ------------------------------------------------------------------ owner

    @Test
    void owner_holdsEveryPermission_evenThoughNoneAreStored() {
        TenantRole owner = TenantRole.fromTemplate(TENANT, SystemRole.OWNER);

        assertThat(owner.permissions()).isEqualTo(Permission.all());
        assertThat(storedKeys(owner)).isEmpty();
        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.isLocked()).isTrue();
    }

    @Test
    void owner_cannotBeChanged() {
        TenantRole owner = TenantRole.fromTemplate(TENANT, SystemRole.OWNER);

        assertThatThrownBy(() -> owner.update("Boss", null, Set.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The Owner role can't be changed. It always has every permission.");
        assertThat(owner.getName()).isEqualTo("Owner");
    }

    // ---------------------------------------------------------- default roles

    @Test
    void recruiterAndInterviewer_startFromTheirTemplates_andCanBeEdited() {
        TenantRole recruiter = TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER);
        TenantRole interviewer = TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER);

        assertThat(recruiter.permissions()).isEqualTo(SystemRole.RECRUITER.defaultPermissions());
        assertThat(interviewer.permissions()).containsExactly(Permission.INTERVIEWS_CONDUCT);
        assertThat(recruiter.isLocked()).isFalse();

        interviewer.update("Panel member", "Sits on interview panels.",
                EnumSet.of(Permission.INTERVIEWS_CONDUCT, Permission.TEAM_VIEW));
        assertThat(interviewer.getName()).isEqualTo("Panel member");
        assertThat(interviewer.getSystemRole()).isEqualTo(SystemRole.INTERVIEWER);
    }

    @Test
    void templates_replaceTheOldFixedRolesOneForOne() {
        assertThat(SystemRole.forLegacyRole(RoleName.COMPANY_ADMIN)).contains(SystemRole.OWNER);
        assertThat(SystemRole.forLegacyRole(RoleName.HR_MANAGER)).contains(SystemRole.RECRUITER);
        assertThat(SystemRole.forLegacyRole(RoleName.INTERVIEWER)).contains(SystemRole.INTERVIEWER);
        assertThat(SystemRole.forLegacyRole(RoleName.CANDIDATE)).isEmpty();
        assertThat(SystemRole.forLegacyRole(RoleName.SUPER_ADMIN)).isEmpty();
    }

    @Test
    void recruiterTemplate_keepsWhatAnHrManagerCouldDo_andNoAdministration() {
        // What HR_MANAGER could do before: manage and publish vacancies.
        assertThat(SystemRole.RECRUITER.defaultPermissions())
                .contains(Permission.JOBS_VIEW_ALL, Permission.JOBS_MANAGE, Permission.JOBS_PUBLISH)
                // ...and still not what was COMPANY_ADMIN-only.
                .doesNotContain(Permission.TEAM_VIEW, Permission.TEAM_INVITE, Permission.ROLES_MANAGE,
                        Permission.SETTINGS_EDIT);
    }

    // ------------------------------------------------------------ custom roles

    @Test
    void custom_storesRequirementsAlongsideWhatWasChosen() {
        TenantRole coordinator = TenantRole.custom(TENANT, "Hiring Coordinator", null,
                EnumSet.of(Permission.TEAM_INVITE, Permission.INTERVIEWS_SCHEDULE));

        assertThat(coordinator.permissions()).containsExactlyInAnyOrder(
                Permission.TEAM_INVITE, Permission.TEAM_VIEW, Permission.INTERVIEWS_SCHEDULE);
        assertThat(coordinator.getSystemRole()).isNull();
        assertThat(coordinator.isLocked()).isFalse();
    }

    @Test
    void aStoredKeyNoLongerInTheCatalogue_isIgnored_ratherThanFailingTheRole() {
        TenantRole role = TenantRole.custom(TENANT, "Legacy", null, EnumSet.of(Permission.TEAM_VIEW));
        storedKeys(role).add("reports.export");

        assertThat(role.permissions()).containsExactly(Permission.TEAM_VIEW);
    }

    @Test
    void aRoleCanHoldNoPermissionsAtAll() {
        TenantRole observer = TenantRole.custom(TENANT, "Observer", null, EnumSet.noneOf(Permission.class));

        assertThat(observer.permissions()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> storedKeys(TenantRole role) {
        return (Set<String>) ReflectionTestUtils.getField(role, "permissionKeys");
    }
}
