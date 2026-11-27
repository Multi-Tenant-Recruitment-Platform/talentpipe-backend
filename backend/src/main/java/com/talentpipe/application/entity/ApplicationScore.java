package com.talentpipe.application.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The result of an application's latest screening (V26).
 *
 * <p>One row per application: screening it again is {@link #recordSuccess} or
 * {@link #recordFailure} on the row it already has. The row always describes the
 * latest attempt, so a failed one clears the score before it rather than leave
 * it looking current.</p>
 *
 * <p>The database keeps the score in the same company as its application
 * (composite foreign key, V26).</p>
 */
@Entity
@Table(name = "application_scores")
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class ApplicationScore extends BaseEntity {

    static final int MAX_FAILURE_REASON = 500;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScreeningStatus status;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", nullable = false, columnDefinition = "jsonb")
    private JsonStrings strengths = JsonStrings.none();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps", nullable = false, columnDefinition = "jsonb")
    private JsonStrings gaps = JsonStrings.none();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criterion_scores", nullable = false, columnDefinition = "jsonb")
    private List<CriterionScore> criterionScores = new ArrayList<>();

    @Column(name = "model_version", length = 80)
    private String modelVersion;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "cached", nullable = false)
    private boolean cached;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "scored_at")
    private Instant scoredAt;

    /** The band the score fell in (V29); null for a failed screening, or one scored before V29. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private ScreeningOutcome outcome;

    /** The thresholds {@link #outcome} was decided against, kept with it. */
    @Column(name = "auto_threshold_used")
    private Integer autoThresholdUsed;

    @Column(name = "manual_threshold_used")
    private Integer manualThresholdUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills", nullable = false, columnDefinition = "jsonb")
    private JsonStrings matchedSkills = JsonStrings.none();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills", nullable = false, columnDefinition = "jsonb")
    private JsonStrings missingSkills = JsonStrings.none();

    protected ApplicationScore() {
        // for JPA
    }

    private ApplicationScore(UUID applicationId, UUID tenantId) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    /** The first screening of an application, finished. */
    public static ApplicationScore createScored(UUID applicationId, UUID tenantId,
                                                ScreeningResult result, Instant scoredAt) {
        ApplicationScore score = new ApplicationScore(applicationId, tenantId);
        score.recordSuccess(result, scoredAt);
        return score;
    }

    /** The first screening of an application, failed. */
    public static ApplicationScore createFailed(UUID applicationId, UUID tenantId, String failureReason) {
        ApplicationScore score = new ApplicationScore(applicationId, tenantId);
        score.recordFailure(failureReason);
        return score;
    }

    /** Replaces whatever was recorded before with this result. */
    public void recordSuccess(ScreeningResult result, Instant scoredAt) {
        this.status = ScreeningStatus.SCORED;
        this.overallScore = result.overallScore();
        this.summary = result.summary();
        this.strengths = JsonStrings.of(result.strengths());
        this.gaps = JsonStrings.of(result.gaps());
        this.criterionScores = new ArrayList<>(result.criterionScores());
        this.modelVersion = result.modelVersion();
        this.promptVersion = result.promptVersion();
        this.cached = result.cached();
        this.failureReason = null;
        this.scoredAt = scoredAt;
        this.matchedSkills = JsonStrings.of(result.matchedSkills());
        this.missingSkills = JsonStrings.of(result.missingSkills());
        // A new score needs a new verdict: until {@link #judge} gives one, none.
        this.outcome = null;
        this.autoThresholdUsed = null;
        this.manualThresholdUsed = null;
    }

    /**
     * Decides which band the recorded score falls in, and keeps the thresholds
     * that decided it.
     *
     * @throws IllegalStateException when there is no score to judge
     */
    public ScreeningOutcome judge(ScreeningBands bands) {
        if (status != ScreeningStatus.SCORED || overallScore == null) {
            throw new IllegalStateException("Only a scored screening has an outcome");
        }
        this.outcome = bands.outcomeOf(overallScore);
        this.autoThresholdUsed = bands.auto();
        this.manualThresholdUsed = bands.manual();
        return outcome;
    }

    /**
     * Replaces whatever was recorded before with a failure, keeping a truncated
     * reason. The reason is shown to recruiters, so it must not quote the CV.
     */
    public void recordFailure(String reason) {
        this.status = ScreeningStatus.SCORING_FAILED;
        this.overallScore = null;
        this.summary = null;
        this.strengths = JsonStrings.none();
        this.gaps = JsonStrings.none();
        this.criterionScores = new ArrayList<>();
        this.modelVersion = null;
        this.promptVersion = null;
        this.cached = false;
        this.failureReason = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), MAX_FAILURE_REASON));
        this.scoredAt = null;
        this.matchedSkills = JsonStrings.none();
        this.missingSkills = JsonStrings.none();
        this.outcome = null;
        this.autoThresholdUsed = null;
        this.manualThresholdUsed = null;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ScreeningStatus getStatus() {
        return status;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getStrengths() {
        return strengths.values();
    }

    public List<String> getGaps() {
        return gaps.values();
    }

    public List<CriterionScore> getCriterionScores() {
        return List.copyOf(criterionScores);
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public boolean isCached() {
        return cached;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public ScreeningOutcome getOutcome() {
        return outcome;
    }

    public Integer getAutoThresholdUsed() {
        return autoThresholdUsed;
    }

    public Integer getManualThresholdUsed() {
        return manualThresholdUsed;
    }

    public List<String> getMatchedSkills() {
        return matchedSkills.values();
    }

    public List<String> getMissingSkills() {
        return missingSkills.values();
    }
}
