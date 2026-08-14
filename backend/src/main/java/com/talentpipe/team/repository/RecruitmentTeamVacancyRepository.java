package com.talentpipe.team.repository;

import com.talentpipe.team.entity.RecruitmentTeamVacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecruitmentTeamVacancyRepository extends JpaRepository<RecruitmentTeamVacancy, UUID> {

    List<RecruitmentTeamVacancy> findAllByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    List<RecruitmentTeamVacancy> findAllByTenantIdAndVacancyId(UUID tenantId, UUID vacancyId);

    Optional<RecruitmentTeamVacancy> findByTenantIdAndTeamIdAndVacancyId(UUID tenantId, UUID teamId, UUID vacancyId);

    boolean existsByTeamIdAndVacancyId(UUID teamId, UUID vacancyId);

    void deleteByTenantIdAndTeamIdAndVacancyId(UUID tenantId, UUID teamId, UUID vacancyId);

    void deleteAllByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    long countByTenantIdAndTeamId(UUID tenantId, UUID teamId);
}
