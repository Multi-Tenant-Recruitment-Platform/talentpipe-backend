package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobStagePanel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The panels of vacancies' interview rounds. Callers load the vacancy through
 * {@code VacancyAccess} first, so a lookup by vacancy id is already scoped to
 * what the caller may see; the ORM tenant filter scopes it again.
 */
public interface JobStagePanelRepository extends JpaRepository<JobStagePanel, UUID> {

    /** Every panel of one vacancy. */
    List<JobStagePanel> findAllByJobId(UUID jobId);
}
