package com.talentpipe.team.repository;

import com.talentpipe.team.entity.RecruitmentTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecruitmentTeamMemberRepository extends JpaRepository<RecruitmentTeamMember, UUID> {

    List<RecruitmentTeamMember> findAllByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    Optional<RecruitmentTeamMember> findByTenantIdAndTeamIdAndUserId(UUID tenantId, UUID teamId, UUID userId);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    void deleteByTenantIdAndTeamIdAndUserId(UUID tenantId, UUID teamId, UUID userId);

    void deleteAllByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    long countByTenantIdAndTeamId(UUID tenantId, UUID teamId);
}
