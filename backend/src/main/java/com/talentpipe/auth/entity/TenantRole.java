package com.talentpipe.auth.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.tenant.TenantFilters;
import com.talentpipe.security.Permission;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.Filter;

/**
 * A role defined by one company: a name of its choosing and the set of
 * {@link Permission}s ("tasks") its members may perform.
 *
 * <p>Every workspace starts with the three {@link SystemRole} roles and can
 * create its own — a "Hiring Coordinator" who manages the team and organises
 * interviews, a "Panel Interviewer" who only conducts them. Each company user
 * holds exactly one role.</p>
 *
 * <p>Invariants this class keeps, whichever caller reaches it:</p>
 * <ul>
 *   <li>The Owner role cannot be renamed, edited or given a permission set —
 *       it always holds every permission ({@link #permissions()}).</li>
 *   <li>The stored set is closed under {@link Permission#requires()}: a role
 *       that can publish vacancies can also edit them.</li>
 * </ul>
 * <p>Who may change a role, and the rules that need the database (unique
 * names, the last Owner, no privilege escalation), live in
 * {@code TenantRoleService}.</p>
 *
 * <p>Permissions are stored as their keys. A key that is no longer in the
 * catalogue is ignored on read rather than failing the load, so retiring a
 * permission can never stop a workspace from signing in.</p>
 */
@Entity
@Table(name = "tenant_roles")
// Tenant-scoped: a company's roles joins the ORM tenant filter defined on User, so a
// query made while a company is in context only ever sees that company's
// rows (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class TenantRole extends BaseEntity {

    /** Column widths, mirrored by the request DTO's constraints. */
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 300;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "description", length = MAX_DESCRIPTION_LENGTH)
    private String description;

    /** Which default template this role is, or {@code null} for a role the company created. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", length = 20, updatable = false)
    private SystemRole systemRole;

    /** Optimistic lock: two admins editing one role cannot silently overwrite each other. */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    private Set<String> permissionKeys = new HashSet<>();

    protected TenantRole() {
        // for JPA
    }

    private TenantRole(UUID tenantId, String name, String description, SystemRole systemRole,
                       Set<Permission> permissions) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        storePermissions(permissions);
    }

    /** A workspace's copy of a default role. Owner's permission set is implicit, so none is stored. */
    public static TenantRole fromTemplate(UUID tenantId, SystemRole template) {
        Set<Permission> stored = template == SystemRole.OWNER
                ? EnumSet.noneOf(Permission.class)
                : template.defaultPermissions();
        return new TenantRole(tenantId, template.displayName(), template.description(), template, stored);
    }

    /** A role the company defines itself. */
    public static TenantRole custom(UUID tenantId, String name, String description, Set<Permission> permissions) {
        return new TenantRole(tenantId, name, description, null, permissions);
    }

    // ------------------------------------------------------------- behaviour

    /**
     * Replaces the role's name, description and permissions.
     *
     * @throws BusinessRuleException (422) for the Owner role, which cannot change
     */
    public void update(String newName, String newDescription, Set<Permission> newPermissions) {
        requireEditable();
        this.name = newName;
        this.description = newDescription;
        storePermissions(newPermissions);
    }

    /**
     * What members of this role may do. For Owner that is every permission in
     * the catalogue, whatever is stored.
     */
    public Set<Permission> permissions() {
        if (systemRole == SystemRole.OWNER) {
            return Permission.all();
        }
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        permissionKeys.forEach(key -> Permission.fromKey(key).ifPresent(permissions::add));
        return permissions;
    }

    /** @throws BusinessRuleException (422) for the Owner role */
    public void requireEditable() {
        if (isLocked()) {
            throw new BusinessRuleException("The Owner role can't be changed. It always has every permission.");
        }
    }

    public boolean isOwner() {
        return systemRole == SystemRole.OWNER;
    }

    public boolean isLocked() {
        return systemRole != null && systemRole.isLocked();
    }

    // -------------------------------------------------------------- getters

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** The default template this role came from, or {@code null} for a custom role. */
    public SystemRole getSystemRole() {
        return systemRole;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "TenantRole{id=" + getId() + ", tenantId=" + tenantId + ", systemRole=" + systemRole + "}";
    }

    private void storePermissions(Set<Permission> permissions) {
        this.permissionKeys.clear();
        this.permissionKeys.addAll(Permission.withRequirements(permissions).stream()
                .map(Permission::key)
                .collect(Collectors.toSet()));
    }
}
