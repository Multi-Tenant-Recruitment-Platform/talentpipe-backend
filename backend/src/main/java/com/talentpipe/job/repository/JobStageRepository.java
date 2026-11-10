package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobStage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The stages vacancies run on. Callers load the vacancy through
 * {@code VacancyAccess} first, so a lookup by vacancy id is already scoped to
 * what the caller may see; the ORM tenant filter scopes it again.
 */
public interface JobStageRepository extends JpaRepository<JobStage, UUID> {

    /** A vacancy's stages, first to last. */
    List<JobStage> findAllByJobIdOrderByPositionAsc(UUID jobId);
}
