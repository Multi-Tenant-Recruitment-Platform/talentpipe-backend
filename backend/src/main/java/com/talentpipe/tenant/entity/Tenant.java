package com.talentpipe.tenant.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A customer company. Root of all tenant-scoped data on the platform.
 *
 * <p>Module boundary note: this entity is private to the tenant module. Other
 * modules reference tenants by {@code UUID} and talk to {@code TenantService},
 * never to this class.</p>
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Unique, URL-safe identifier used to resolve the tenant at login. */
    @Column(name = "subdomain", nullable = false, unique = true, length = 100)
    private String subdomain;

    @Column(name = "industry", length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 30)
    private PlanTier planTier = PlanTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    protected Tenant() {
        // for JPA
    }

    public Tenant(String name, String subdomain) {
        this.name = name;
        this.subdomain = subdomain;
    }

    public String getName() {
        return name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String getIndustry() {
        return industry;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public TenantStatus getStatus() {
        return status;
    }
}
