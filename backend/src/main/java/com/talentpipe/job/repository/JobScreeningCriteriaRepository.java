package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobScreeningCriteria;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * What vacancies score their applications on. Callers load the vacancy through
 * {@code VacancyAccess} first, so a lookup by vacancy id is already scoped to
 * what the caller may see; the ORM tenant filter scopes it again.
 */
public interface JobScreeningCriteriaRepository extends JpaRepository<JobScreeningCriteria, UUID> {

    /** A vacancy's criteria, by name. */
    List<JobScreeningCriteria> findAllByJobIdOrderByNameAsc(UUID jobId);
}
