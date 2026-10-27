package com.talentpipe.security;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The platform's permission catalogue — every task a company member can be
 * allowed to do, and the single source of truth for what a role can contain.
 *
 * <h2>Why the catalogue lives in code</h2>
 * <p>Companies build their own roles (see {@code TenantRole}), but only out of
 * these permissions. A company cannot invent a new one: a permission means
 * something only because an endpoint checks it, so a permission with no code
 * behind it would be a checkbox that does nothing. Adding a task to the
 * product is therefore a code change — a new constant here plus the endpoint
 * that enforces it.</p>
 *
 * <h2>How a permission is checked</h2>
 * <p>Each permission's {@link #key()} is a Spring Security authority. A
 * company user's request carries the authorities of their current role
 * (resolved per request by {@link PermissionResolver}), and endpoints guard
 * themselves with {@link RequirePermission}:</p>
 * <pre>{@code @RequirePermission(Permission.Keys.JOBS_MANAGE)}</pre>
 * <p>A build test fails if any {@code @RequirePermission} names a key that is
 * not in this catalogue.</p>
 *
 * <h2>Rules for changing it</h2>
 * <ul>
 *   <li>Keys are stored in {@code tenant_role_permissions}: never rename one.
 *       Retiring a key needs a migration that deletes its rows.</li>
 *   <li>Adding a key is safe — no existing role gains it, except the Owner
 *       role, which always holds every permission.</li>
 * </ul>
 */
public enum Permission {

    // ------------------------------------------------------------------ team
    TEAM_VIEW(Keys.TEAM_VIEW, Group.TEAM,
            "View team", "See the people in the workspace and pending invitations."),
    TEAM_INVITE(Keys.TEAM_INVITE, Group.TEAM,
            "Invite people", "Invite new members, and re-send or revoke invitations.",
            Keys.TEAM_VIEW),
    TEAM_MANAGE(Keys.TEAM_MANAGE, Group.TEAM,
            "Manage members", "Change which role a member has.",
            Keys.TEAM_VIEW),
    ROLES_MANAGE(Keys.ROLES_MANAGE, Group.TEAM,
            "Manage roles", "Create, edit and delete the workspace's roles.",
            Keys.TEAM_VIEW),

    // --------------------------------------------------------------- company
    SETTINGS_EDIT(Keys.SETTINGS_EDIT, Group.COMPANY,
            "Edit company profile", "Change the company profile, logo, cover image and workspace settings."),

    // ------------------------------------------------------------- vacancies
    JOBS_VIEW_ALL(Keys.JOBS_VIEW_ALL, Group.VACANCIES,
            "See all vacancies",
            "See every vacancy in the workspace. Without this, people see only the vacancies "
                    + "they work on: ones they created, recruit for, manage or are on the hiring team of."),
    JOBS_MANAGE(Keys.JOBS_MANAGE, Group.VACANCIES,
            "Create and edit vacancies",
            "Create, edit and duplicate vacancies, change their AI settings and choose their hiring team."),
    JOBS_PUBLISH(Keys.JOBS_PUBLISH, Group.VACANCIES,
            "Publish vacancies", "Publish, close and archive vacancies.",
            Keys.JOBS_MANAGE),

    // ---------------------------------------------------------------- hiring
    PIPELINE_TEMPLATES_MANAGE(Keys.PIPELINE_TEMPLATES_MANAGE, Group.HIRING,
            "Manage pipeline templates", "Create and edit the hiring pipelines vacancies follow."),
    APPLICATIONS_VIEW_ALL(Keys.APPLICATIONS_VIEW_ALL, Group.HIRING,
            "See all applicants",
            "See applications to every vacancy. Without this, people see only applicants to "
                    + "vacancies they are on the hiring team of."),
    APPLICATIONS_MANAGE(Keys.APPLICATIONS_MANAGE, Group.HIRING,
            "Move applicants", "Move applicants between pipeline stages and reject them."),

    // ------------------------------------------------------------ interviews
    INTERVIEWS_SCHEDULE(Keys.INTERVIEWS_SCHEDULE, Group.INTERVIEWS,
            "Organise interviews",
            "Schedule and reschedule interviews, choose the interviewers and add the meeting link."),
    INTERVIEWS_CONDUCT(Keys.INTERVIEWS_CONDUCT, Group.INTERVIEWS,
            "Conduct interviews",
            "See the interviews you are invited to, join them and submit feedback.");

    /**
     * The keys as compile-time constants, so annotations can reference them
     * ({@code @RequirePermission(Permission.Keys.JOBS_MANAGE)}) and a typo is
     * a compile error rather than an endpoint nobody can call.
     */
    public static final class Keys {
        public static final String TEAM_VIEW = "team.view";
        public static final String TEAM_INVITE = "team.invite";
        public static final String TEAM_MANAGE = "team.manage";
        public static final String ROLES_MANAGE = "roles.manage";
        public static final String SETTINGS_EDIT = "settings.edit";
        public static final String JOBS_VIEW_ALL = "jobs.view.all";
        public static final String JOBS_MANAGE = "jobs.manage";
        public static final String JOBS_PUBLISH = "jobs.publish";
        public static final String PIPELINE_TEMPLATES_MANAGE = "pipeline.templates.manage";
        public static final String APPLICATIONS_VIEW_ALL = "applications.view.all";
        public static final String APPLICATIONS_MANAGE = "applications.manage";
        public static final String INTERVIEWS_SCHEDULE = "interviews.schedule";
        public static final String INTERVIEWS_CONDUCT = "interviews.conduct";

        private Keys() {
            // constants only
        }
    }

    /** How the role editor groups permissions into tasks. Declaration order is display order. */
    public enum Group {
        TEAM("Team and access"),
        COMPANY("Company profile"),
        VACANCIES("Vacancies"),
        HIRING("Applicants and pipelines"),
        INTERVIEWS("Interviews");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, Permission> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::key, Function.identity()));

    private final String key;
    private final Group group;
    private final String label;
    private final String description;
    private final String[] requiredKeys;

    Permission(String key, Group group, String label, String description, String... requiredKeys) {
        this.key = key;
        this.group = group;
        this.label = label;
        this.description = description;
        this.requiredKeys = requiredKeys;
    }

    /** The stored and checked identifier, e.g. {@code jobs.manage}. */
    public String key() {
        return key;
    }

    public Group group() {
        return group;
    }

    /** Short name shown next to the checkbox in the role editor. */
    public String label() {
        return label;
    }

    /** One sentence saying what the permission allows. */
    public String description() {
        return description;
    }

    /**
     * Permissions this one is meaningless without — inviting people requires
     * seeing the team. A role that holds this permission always holds these.
     */
    public Set<Permission> requires() {
        EnumSet<Permission> required = EnumSet.noneOf(Permission.class);
        for (String requiredKey : requiredKeys) {
            required.add(BY_KEY.get(requiredKey));
        }
        return required;
    }

    /** The permission with this key, if the catalogue still has one. */
    public static Optional<Permission> fromKey(String key) {
        return Optional.ofNullable(key == null ? null : BY_KEY.get(key));
    }

    /** Every permission — what the Owner role holds. */
    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }

    /**
     * {@code permissions} plus everything they require, transitively — the set
     * a role actually stores. Ticking "Publish vacancies" in the editor
     * therefore also stores "Create and edit vacancies".
     */
    public static Set<Permission> withRequirements(Collection<Permission> permissions) {
        EnumSet<Permission> closed = EnumSet.noneOf(Permission.class);
        Deque<Permission> pending = new ArrayDeque<>(permissions);
        while (!pending.isEmpty()) {
            Permission next = pending.pop();
            if (closed.add(next)) {
                pending.addAll(next.requires());
            }
        }
        return closed;
    }
}
