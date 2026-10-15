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
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.service.VacancyApplicationGate;
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
    @Mock private VacancyApplicationGate vacancyApplicationGate;
    @Mock private JobVacancyRepository vacancyRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private ApplicationService service;
    private UUID candidateId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, candidateRepository,
                vacancyApplicationGate, vacancyRepository, userRepository, events);
        candidateId = UUID.randomUUID();
        jobId = UUID.randomUUID();
    }

    @Test
    void submit_rejectsDuplicateBeforeSaving() {
        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(vacancyApplicationGate.requireOpen(jobId))
                .thenReturn(new VacancyApplicationTarget(jobId, UUID.randomUUID(), "Backend Engineer"));
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(candidateId, jobId.toString()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already applied");
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void submit_savesApplicationForPublishedJob() {
        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(vacancyApplicationGate.requireOpen(jobId))
                .thenReturn(new VacancyApplicationTarget(jobId, UUID.randomUUID(), "Backend Engineer"));
        when(applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(candidateId, jobId.toString());

        verify(applicationRepository).save(any(Application.class));
    }
}
