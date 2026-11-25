package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobStageInterviewer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The people on vacancies' interview panels. Scoped as {@link JobStagePanelRepository} is. */
public interface JobStageInterviewerRepository
        extends JpaRepository<JobStageInterviewer, JobStageInterviewer.Key> {

    /** Everyone on any panel of one vacancy, longest-standing first within each panel. */
    List<JobStageInterviewer> findAllByJobIdOrderByAddedAtAscUserIdAsc(UUID jobId);
}
