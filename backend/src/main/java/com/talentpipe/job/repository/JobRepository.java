package com.talentpipe.job.repository;

import com.talentpipe.job.entity.Job;
import com.talentpipe.job.entity.JobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findAllByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);

    List<Job> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
