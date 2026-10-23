package com.talentpipe.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the {@link Permission} catalogue. Keys are stored in the
 * database and checked by endpoints, so their shape and stability matter.
 */
class PermissionTest {

    @Test
    void keysAreUnique() {
        List<String> keys = Arrays.stream(Permission.values()).map(Permission::key).toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void everyKeyIsLowerCaseDottedAndFitsTheColumn(Permission permission) {
        assertThat(permission.key())
                .matches("[a-z]+(\\.[a-z]+)+")
                .hasSizeLessThanOrEqualTo(60);
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void everyPermissionIsDescribedForTheRoleEditor(Permission permission) {
        assertThat(permission.label()).isNotBlank();
        assertThat(permission.description()).isNotBlank().endsWith(".");
        assertThat(permission.group()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void noPermissionRequiresItself(Permission permission) {
        assertThat(permission.requires()).doesNotContain(permission);
    }

    @Test
    void fromKey_findsEachPermission_andNothingElse() {
        for (Permission permission : Permission.values()) {
            assertThat(Permission.fromKey(permission.key())).contains(permission);
        }
        assertThat(Permission.fromKey("jobs.delete")).isEmpty();
        assertThat(Permission.fromKey(null)).isEmpty();
    }

    @Test
    void keysConstantsMatchTheEnum() {
        assertThat(Permission.JOBS_MANAGE.key()).isEqualTo(Permission.Keys.JOBS_MANAGE);
        assertThat(Permission.TEAM_VIEW.key()).isEqualTo(Permission.Keys.TEAM_VIEW);
        assertThat(Permission.INTERVIEWS_CONDUCT.key()).isEqualTo(Permission.Keys.INTERVIEWS_CONDUCT);
    }

    @Test
    void withRequirements_addsWhatAPermissionDependsOn() {
        assertThat(Permission.withRequirements(Set.of(Permission.JOBS_PUBLISH)))
                .containsExactlyInAnyOrder(Permission.JOBS_PUBLISH, Permission.JOBS_MANAGE);
        assertThat(Permission.withRequirements(Set.of(Permission.TEAM_INVITE, Permission.ROLES_MANAGE)))
                .containsExactlyInAnyOrder(Permission.TEAM_INVITE, Permission.ROLES_MANAGE, Permission.TEAM_VIEW);
    }

    @Test
    void withRequirements_ofNothing_isNothing() {
        assertThat(Permission.withRequirements(EnumSet.noneOf(Permission.class))).isEmpty();
    }

    @Test
    void withRequirements_ofEverything_isEverything() {
        assertThat(Permission.withRequirements(Permission.all())).isEqualTo(Permission.all());
    }
}
