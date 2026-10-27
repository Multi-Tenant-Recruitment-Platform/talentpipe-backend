package com.talentpipe.auth.entity;

import com.talentpipe.security.Permission;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The three roles every workspace starts with. They replace the old fixed
 * COMPANY_ADMIN / HR_MANAGER / INTERVIEWER roles one for one, so existing
 * members keep exactly the access they had.
 *
 * <p>Each company gets its own editable copy of Recruiter and Interviewer
 * (rows in {@code tenant_roles} with this key) and can add roles of its own
 * alongside them. Owner is the exception: it cannot be edited or deleted, and
 * always holds every permission — including ones added to the catalogue after
 * the workspace was created — so a company can never lock itself out.</p>
 *
 * <p>{@link #legacyRole()} links each template to the old fixed role. It is
 * kept in {@code users.role_id} for members of these roles during the move to
 * permissions, and drives the transitional {@code ROLE_*} authorities (see
 * {@code Authorities}).</p>
 */
public enum SystemRole {

    OWNER("Owner",
            "Runs the workspace. Holds every permission, always.",
            RoleName.COMPANY_ADMIN,
            Permission.all()),

    RECRUITER("Recruiter",
            "Runs hiring day to day: vacancies, applicants and interviews.",
            RoleName.HR_MANAGER,
            EnumSet.of(
                    Permission.JOBS_VIEW_ALL,
                    Permission.JOBS_MANAGE,
                    Permission.JOBS_PUBLISH,
                    Permission.APPLICATIONS_VIEW_ALL,
                    Permission.APPLICATIONS_MANAGE,
                    Permission.INTERVIEWS_SCHEDULE)),

    INTERVIEWER("Interviewer",
            "Interviews candidates for the vacancies they are assigned to.",
            RoleName.INTERVIEWER,
            EnumSet.of(Permission.INTERVIEWS_CONDUCT));

    private final String displayName;
    private final String description;
    private final RoleName legacyRole;
    private final Set<Permission> defaultPermissions;

    SystemRole(String displayName, String description, RoleName legacyRole, Set<Permission> defaultPermissions) {
        this.displayName = displayName;
        this.description = description;
        this.legacyRole = legacyRole;
        this.defaultPermissions = Collections.unmodifiableSet(EnumSet.copyOf(defaultPermissions));
    }

    /** The name the role is created with; companies may rename every role but Owner. */
    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** The fixed role this template replaces. */
    public RoleName legacyRole() {
        return legacyRole;
    }

    /** What a freshly created workspace's copy of this role holds. */
    public Set<Permission> defaultPermissions() {
        return defaultPermissions;
    }

    /** Owner can be neither edited nor deleted. */
    public boolean isLocked() {
        return this == OWNER;
    }

    /** The template that replaced an old fixed role, if that role had one. */
    public static Optional<SystemRole> forLegacyRole(RoleName legacyRole) {
        return Arrays.stream(values()).filter(role -> role.legacyRole == legacyRole).findFirst();
    }
}
