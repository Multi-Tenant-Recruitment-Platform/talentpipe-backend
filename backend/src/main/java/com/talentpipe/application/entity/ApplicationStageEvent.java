package com.talentpipe.application.entity;

import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One line of an application's history (V29): it was submitted, screened,
 * moved, shortlisted, rejected or withdrawn.
 *
 * <p>Written once, never changed. Every change to an application's review
 * status or stage writes exactly one of these in the same transaction, so the
 * history and the application can never disagree.</p>
 */
@Entity
@Table(name = "application_stage_events")
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
public class ApplicationStageEvent {

    static final int MAX_REASON = 500;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private StageEventType eventType;

    @Column(name = "from_stage_id", updatable = false)
    private UUID fromStageId;

    @Column(name = "to_stage_id", updatable = false)
    private UUID toStageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_review_status", updatable = false, length = 30)
    private ReviewStatus fromReviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_review_status", updatable = false, length = 30)
    private ReviewStatus toReviewStatus;

    /** Null: the system did it. */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "reason", updatable = false, length = MAX_REASON)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApplicationStageEvent() {
        // for JPA
    }

    private ApplicationStageEvent(Application application, StageEventType eventType, UUID fromStageId,
                                  ReviewStatus fromReviewStatus, UUID actorUserId, String reason,
                                  Map<String, Object> metadata, Instant createdAt) {
        this.tenantId = application.getTenantId();
        this.applicationId = Objects.requireNonNull(application.getId(), "application must be saved first");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.fromStageId = fromStageId;
        this.toStageId = application.getCurrentStageId();
        this.fromReviewStatus = fromReviewStatus;
        this.toReviewStatus = application.getReviewStatus();
        this.actorUserId = actorUserId;
        this.reason = reason == null ? null : reason.substring(0, Math.min(reason.length(), MAX_REASON));
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Records what just happened to {@code application}, which already holds
     * its new stage and review status.
     *
     * @param fromStageId      the stage before the change
     * @param fromReviewStatus the review status before the change
     * @param actorUserId      who did it; null for the system
     */
    @SuppressWarnings("java:S107") // one history line needs the whole before/after picture
    public static ApplicationStageEvent record(Application application, StageEventType eventType,
                                               UUID fromStageId, ReviewStatus fromReviewStatus,
                                               UUID actorUserId, String reason,
                                               Map<String, Object> metadata, Instant at) {
        return new ApplicationStageEvent(application, eventType, fromStageId, fromReviewStatus,
                actorUserId, reason, metadata, at);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getApplicationId() { return applicationId; }
    public StageEventType getEventType() { return eventType; }
    public UUID getFromStageId() { return fromStageId; }
    public UUID getToStageId() { return toStageId; }
    public ReviewStatus getFromReviewStatus() { return fromReviewStatus; }
    public ReviewStatus getToReviewStatus() { return toReviewStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public String getReason() { return reason; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Instant getCreatedAt() { return createdAt; }
}
