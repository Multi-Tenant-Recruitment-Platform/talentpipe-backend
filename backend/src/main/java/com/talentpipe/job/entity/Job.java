package com.talentpipe.job.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PUBLISHED;

    protected Job() {
        // for JPA
    }

    public Job(UUID tenantId, String title, String description) {
        this.tenantId = tenantId;
        this.title = title;
        this.description = description;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }
}
