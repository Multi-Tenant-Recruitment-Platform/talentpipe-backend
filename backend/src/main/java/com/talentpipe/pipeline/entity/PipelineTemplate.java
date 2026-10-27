package com.talentpipe.pipeline.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Company-wide recruitment pipeline template defining stages, order, interview
 * benchmarks, and default AI screening thresholds.
 */
@Entity
@Table(name = "pipeline_templates")
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class PipelineTemplate extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "default_auto_threshold", nullable = false)
    private int defaultAutoThreshold = 80;

    @Column(name = "default_manual_threshold", nullable = false)
    private int defaultManualThreshold = 70;

    @Column(name = "ideal_matching_enabled", nullable = false)
    private boolean idealMatchingEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PipelineTemplateStatus status = PipelineTemplateStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PipelineTemplateStage> stages = new ArrayList<>();

    protected PipelineTemplate() {
        // JPA constructor
    }

    public PipelineTemplate(UUID tenantId, String name, String description,
                            int defaultAutoThreshold, int defaultManualThreshold,
                            boolean idealMatchingEnabled, UUID createdBy) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.defaultAutoThreshold = defaultAutoThreshold;
        this.defaultManualThreshold = defaultManualThreshold;
        this.idealMatchingEnabled = idealMatchingEnabled;
        this.createdBy = createdBy;
        this.status = PipelineTemplateStatus.ACTIVE;
    }

    public void addStage(PipelineTemplateStage stage) {
        stages.add(stage);
        stage.setTemplate(this);
    }

    public void clearStages() {
        stages.forEach(stage -> stage.setTemplate(null));
        stages.clear();
    }

    public void setStages(List<PipelineTemplateStage> newStages) {
        clearStages();
        if (newStages != null) {
            newStages.forEach(this::addStage);
        }
    }

    public void archive() {
        this.status = PipelineTemplateStatus.ARCHIVED;
        this.archivedAt = Instant.now();
    }

    public boolean isArchived() {
        return this.status == PipelineTemplateStatus.ARCHIVED;
    }

    public boolean isActive() {
        return this.status == PipelineTemplateStatus.ACTIVE;
    }

    public int getInterviewRoundCount() {
        return (int) stages.stream()
                .filter(stage -> stage.getStageType() == StageType.INTERVIEW)
                .count();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public int getDefaultAutoThreshold() {
        return defaultAutoThreshold;
    }

    public void setDefaultAutoThreshold(int defaultAutoThreshold) {
        this.defaultAutoThreshold = defaultAutoThreshold;
    }

    public int getDefaultManualThreshold() {
        return defaultManualThreshold;
    }

    public void setDefaultManualThreshold(int defaultManualThreshold) {
        this.defaultManualThreshold = defaultManualThreshold;
    }

    public boolean isIdealMatchingEnabled() {
        return idealMatchingEnabled;
    }

    public void setIdealMatchingEnabled(boolean idealMatchingEnabled) {
        this.idealMatchingEnabled = idealMatchingEnabled;
    }

    public PipelineTemplateStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineTemplateStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public List<PipelineTemplateStage> getStages() {
        return stages;
    }
}
