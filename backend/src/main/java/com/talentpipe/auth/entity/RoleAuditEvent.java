package com.talentpipe.auth.entity;

import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One entry in a workspace's access audit trail: a role was created, edited
 * or deleted, or a member was moved to another role.
 *
 * <p>Who can do what is the most sensitive configuration a workspace has, so
 * every change to it is recorded with the person who made it and the value
 * before and after. Rows are written once and never changed; they outlive the
 * roles they describe, which is why the role's name is copied in.</p>
 */
@Entity
@Table(name = "tenant_role_audit_events")
// Tenant-scoped: the role audit trail joins the ORM tenant filter defined on User, so a
// query made while a company is in context only ever sees that company's
// rows (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
public class RoleAuditEvent {

    /** Column width of the before/after values; longer values are cut to fit. */
    static final int MAX_VALUE_LENGTH = 2000;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 30)
    private RoleAuditAction action;

    @Column(name = "role_id", updatable = false)
    private UUID roleId;

    @Column(name = "role_name", nullable = false, updatable = false, length = 60)
    private String roleName;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "previous_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String previousValue;

    @Column(name = "new_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected RoleAuditEvent() {
        // for JPA
    }

    @SuppressWarnings("java:S107") // Audit event constructor requires full audit trail context
    public RoleAuditEvent(UUID tenantId, UUID actorUserId, RoleAuditAction action, UUID roleId,
                          String roleName, UUID targetUserId, String previousValue, String newValue,
                          Instant occurredAt) {
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.roleId = roleId;
        this.roleName = roleName;
        this.targetUserId = targetUserId;
        this.previousValue = fit(previousValue);
        this.newValue = fit(newValue);
        this.occurredAt = occurredAt;
    }

    private static String fit(String value) {
        return value == null || value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public RoleAuditAction getAction() {
        return action;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
