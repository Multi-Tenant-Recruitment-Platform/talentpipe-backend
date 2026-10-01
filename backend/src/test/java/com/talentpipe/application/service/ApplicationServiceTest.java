package com.talentpipe.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.application.entity.Application;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.job.entity.Job;
import com.talentpipe.job.repository.JobRepository;
import com.talentpipe.job.service.JobService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private JobService jobService;
    @Mock private JobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private ApplicationService service;
    private UUID candidateId;
    private UUID jobId;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, candidateRepository, jobService,
                jobRepository, userRepository, events);
        candidateId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        job = new Job(UUID.randomUUID(), "Backend Engineer", "Build APIs");
    }

    @Test
    void submit_rejectsDuplicateBeforeSaving() {
        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(jobService.requirePublished(jobId)).thenReturn(job);
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(candidateId, jobId))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already applied");
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void submit_savesApplicationForPublishedJob() {
        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(jobService.requirePublished(jobId)).thenReturn(job);
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(candidateId, jobId);

        verify(applicationRepository).save(any(Application.class));
    }
}
