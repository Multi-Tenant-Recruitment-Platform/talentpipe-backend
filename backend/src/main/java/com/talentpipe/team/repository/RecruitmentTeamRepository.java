package com.talentpipe.team.repository;

import com.talentpipe.team.entity.RecruitmentTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecruitmentTeamRepository extends JpaRepository<RecruitmentTeam, UUID> {

    List<RecruitmentTeam> findAllByTenantId(UUID tenantId);

    List<RecruitmentTeam> findAllByTenantIdAndDepartmentIgnoreCase(UUID tenantId, String department);

    Optional<RecruitmentTeam> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
