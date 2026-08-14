package com.talentpipe.team.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Represents a vacancy/job mapped to a recruitment team.
 */
@Entity
@Table(name = "recruitment_team_vacancies")
public class RecruitmentTeamVacancy extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "vacancy_id", nullable = false, updatable = false)
    private UUID vacancyId;

    protected RecruitmentTeamVacancy() {
        // for JPA
    }

    public RecruitmentTeamVacancy(UUID tenantId, UUID teamId, UUID vacancyId) {
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.vacancyId = vacancyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getVacancyId() {
        return vacancyId;
    }

    @Override
    public String toString() {
        return "RecruitmentTeamVacancy{id=" + getId() + ", teamId=" + teamId + ", vacancyId=" + vacancyId + "}";
    }
}
