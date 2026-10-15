package com.talentpipe.job.service;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.CreateJobRequest;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.mapper.JobSlug;
import com.talentpipe.job.entity.Job;
import com.talentpipe.job.entity.JobStatus;
import com.talentpipe.job.repository.JobRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public JobSummaryResponse create(UUID tenantId, CreateJobRequest request) {
        return toResponse(jobRepository.save(new Job(tenantId, request.title().trim(), request.description().trim())));
    }

    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> listPublished(int page, int size) {
        return jobRepository.findAllByStatusOrderByCreatedAtDesc(JobStatus.PUBLISHED, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Job requirePublished(UUID jobId) {
        return jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == JobStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published job not found"));
    }

    private JobSummaryResponse toResponse(Job job) {
        return new JobSummaryResponse(
                job.getId(),
                JobSlug.of(job.getTitle(), "", job.getId()),
                job.getTitle(),
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                job.getDescription(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                job.getCreatedAt(),
                job.getStatus() == JobStatus.PUBLISHED);
    }
}
