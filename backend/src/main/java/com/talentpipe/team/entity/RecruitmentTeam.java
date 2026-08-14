package com.talentpipe.team.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Represents a recruitment team within a tenant organization.
 */
@Entity
@Table(name = "recruitment_teams")
public class RecruitmentTeam extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "lead_id")
    private UUID leadId;

    protected RecruitmentTeam() {
        // for JPA
    }

    public RecruitmentTeam(UUID tenantId, String name, String description, String department, UUID leadId) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.department = department;
        this.leadId = leadId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    @Override
    public String toString() {
        return "RecruitmentTeam{id=" + getId() + ", tenantId=" + tenantId + ", name='" + name + "'}";
    }
}
