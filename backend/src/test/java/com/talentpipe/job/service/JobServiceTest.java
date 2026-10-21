package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.CreateJobRequest;
import com.talentpipe.job.entity.Job;
import com.talentpipe.job.entity.JobStatus;
import com.talentpipe.job.repository.JobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository repository;

    @Test
    void createTrimsInputAndReturnsSummary() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Job job = jobWithId(tenantId);
        when(repository.save(any(Job.class))).thenReturn(job);

        var result = new JobService(repository).create(tenantId,
                new CreateJobRequest("  Title  ", "  Description  "));

        assertThat(result.title()).isEqualTo("Title");
        assertThat(result).isNotNull();
    }

    @Test
    void listPublishedMapsRepositoryPage() throws Exception {
        Job job = jobWithId(UUID.randomUUID());
        when(repository.findAllByStatusOrderByCreatedAtDesc(
                JobStatus.PUBLISHED, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(job)));

        assertThat(new JobService(repository).listPublished(0, 10).getContent())
                .singleElement().extracting(summary -> summary.title()).isEqualTo("Title");
    }

    @Test
    void requirePublishedRejectsMissingOrUnpublishedJobs() throws Exception {
        UUID id = UUID.randomUUID();
        Job draft = jobWithId(UUID.randomUUID());
        draft.setStatus(JobStatus.CLOSED);
        when(repository.findById(id)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> new JobService(repository).requirePublished(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Job jobWithId(UUID tenantId) throws Exception {
        Job job = new Job(tenantId, "Title", "Description");
        Field id = job.getClass().getSuperclass().getDeclaredField("id");
        id.setAccessible(true);
        id.set(job, UUID.randomUUID());
        return job;
    }
}
