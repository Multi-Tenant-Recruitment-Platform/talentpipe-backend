package com.talentpipe.job.entity;

import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

/**
 * One person on a vacancy's hiring team: someone who works on it besides its
 * assigned recruiter and hiring manager, such as an interviewer.
 *
 * <p>Being on the team decides what a member can <em>see</em>: a vacancy is
 * visible to them even when their role lacks {@code jobs.view.all}. What they
 * can <em>do</em> with it is still decided by their role.</p>
 *
 * <p>The user is a plain id, as on {@link JobVacancy}: the user entity belongs
 * to the auth module. The database keeps both ids in the same tenant (V18).</p>
 *
 * <p>Does not extend {@link com.talentpipe.common.entity.BaseEntity}: the
 * pair of ids is the key, and a row is only ever added or removed.</p>
 */
@Entity
@Table(name = "job_vacancy_team_members")
// Tenant-scoped: a vacancy's hiring team joins the ORM tenant filter defined on User, so a
// query made while a company is in context only ever sees that company's
// rows (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
@IdClass(VacancyTeamMember.Key.class)
public class VacancyTeamMember {

    @Id
    @Column(name = "vacancy_id", nullable = false, updatable = false)
    private UUID vacancyId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "added_by", updatable = false)
    private UUID addedBy;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected VacancyTeamMember() {
        // for JPA
    }

    public VacancyTeamMember(UUID vacancyId, UUID userId, UUID tenantId, UUID addedBy) {
        this.vacancyId = vacancyId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.addedBy = addedBy;
    }

    public UUID getVacancyId() {
        return vacancyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    /** Composite primary key: one row per person per vacancy. */
    public static class Key implements Serializable {

        private UUID vacancyId;
        private UUID userId;

        protected Key() {
            // for JPA
        }

        public Key(UUID vacancyId, UUID userId) {
            this.vacancyId = vacancyId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Key key
                        && Objects.equals(vacancyId, key.vacancyId)
                        && Objects.equals(userId, key.userId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(vacancyId, userId);
        }
    }
}
