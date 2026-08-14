package com.talentpipe.team.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Represents a user assigned to a recruitment team with a specific member role.
 */
@Entity
@Table(name = "recruitment_team_members")
public class RecruitmentTeamMember extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 50)
    private TeamMemberRole memberRole = TeamMemberRole.RECRUITER;

    protected RecruitmentTeamMember() {
        // for JPA
    }

    public RecruitmentTeamMember(UUID tenantId, UUID teamId, UUID userId, TeamMemberRole memberRole) {
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.userId = userId;
        this.memberRole = memberRole != null ? memberRole : TeamMemberRole.RECRUITER;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getUserId() {
        return userId;
    }

    public TeamMemberRole getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(TeamMemberRole memberRole) {
        this.memberRole = memberRole;
    }

    @Override
    public String toString() {
        return "RecruitmentTeamMember{id=" + getId() + ", teamId=" + teamId + ", userId=" + userId + ", role=" + memberRole + "}";
    }
}
