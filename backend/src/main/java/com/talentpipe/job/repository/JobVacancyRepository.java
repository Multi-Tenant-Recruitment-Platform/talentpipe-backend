package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobVacancy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for job vacancies.
 */
@Repository
public interface JobVacancyRepository extends JpaRepository<JobVacancy, UUID> {

    /**
     * Finds vacancies for a tenant sorted by creation date descending.
     */
    Page<JobVacancy> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /**
     * Single-resource lookup strictly scoped to the tenant.
     */
    Optional<JobVacancy> findByIdAndTenantId(UUID id, UUID tenantId);
}
