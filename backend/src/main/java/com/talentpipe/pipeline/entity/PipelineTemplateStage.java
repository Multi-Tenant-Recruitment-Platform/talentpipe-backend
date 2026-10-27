package com.talentpipe.pipeline.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * An individual stage within a pipeline template.
 */
@Entity
@Table(name = "pipeline_template_stages")
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class PipelineTemplateStage extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private PipelineTemplate template;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 30)
    private StageType stageType;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "benchmark")
    private Integer benchmark;

    protected PipelineTemplateStage() {
        // JPA constructor
    }

    public PipelineTemplateStage(UUID tenantId, int position, StageType stageType, String name, Integer benchmark) {
        this.tenantId = tenantId;
        this.position = position;
        this.stageType = stageType;
        this.name = name;
        this.benchmark = benchmark;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public PipelineTemplate getTemplate() {
        return template;
    }

    public void setTemplate(PipelineTemplate template) {
        this.template = template;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public StageType getStageType() {
        return stageType;
    }

    public void setStageType(StageType stageType) {
        this.stageType = stageType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBenchmark() {
        return benchmark;
    }

    public void setBenchmark(Integer benchmark) {
        this.benchmark = benchmark;
    }
}
