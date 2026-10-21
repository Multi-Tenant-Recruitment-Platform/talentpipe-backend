package com.talentpipe.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.application.dto.UpdateApplicationStatusRequest;
import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.service.VacancyApplicationGate;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
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
        String jobSlugOrId = jobId.toString();

        assertThatThrownBy(() -> service.submit(candidateId, jobSlugOrId))
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

    @Test
    void submit_rejectsUnknownCandidate() {
        when(candidateRepository.existsById(candidateId)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(candidateId, jobId.toString()))
                .isInstanceOf(com.talentpipe.common.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Candidate not found");
    }

    @Test
    void candidateAndTenantListsReturnEmptyWhenNoApplicationsExist() {
        when(applicationRepository.findAllByCandidateIdOrderByCreatedAtDesc(candidateId))
                .thenReturn(List.of());
        when(applicationRepository.findAllByTenantIdOrderByCreatedAtDesc(candidateId))
                .thenReturn(List.of());

        assertThat(service.forCandidate(candidateId)).isEmpty();
        assertThat(service.forTenant(candidateId)).isEmpty();
    }

    @Test
    void getRejectsUnknownApplication() {
        when(applicationRepository.findByIdAndCandidateId(jobId, candidateId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForCandidate(candidateId, jobId))
                .isInstanceOf(com.talentpipe.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void withdrawIsIdempotentForAlreadyWithdrawnApplication() {
        Application application = application();
        application.changeStatus(ApplicationStatus.WITHDRAWN);
        when(applicationRepository.findByIdAndCandidateId(jobId, candidateId))
                .thenReturn(Optional.of(application));

        assertThat(service.withdraw(candidateId, jobId).status())
                .isEqualTo(ApplicationStatus.WITHDRAWN.name());
        verify(applicationRepository, never()).save(application);
    }

    @Test
    void withdrawRejectsRejectedApplication() {
        Application application = application();
        application.changeStatus(ApplicationStatus.REJECTED);
        when(applicationRepository.findByIdAndCandidateId(jobId, candidateId))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.withdraw(candidateId, jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be withdrawn");
    }

    @Test
    void withdrawSavesAndNotifiesForActiveApplication() {
        Application application = application();
        when(applicationRepository.findByIdAndCandidateId(jobId, candidateId))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);
        when(userRepository.findAllByTenantIdAndRoleNameIn(any(), any())).thenReturn(List.of());

        assertThat(service.withdraw(candidateId, jobId).status())
                .isEqualTo(ApplicationStatus.WITHDRAWN.name());
        verify(applicationRepository).save(application);
    }

    @Test
    void updateStatusRejectsInvalidTransitionsAndUpdatesActiveApplication() {
        Application application = application();
        when(applicationRepository.findById(jobId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        assertThatThrownBy(() -> service.updateStatus(candidateId, jobId,
                new UpdateApplicationStatusRequest(ApplicationStatus.WITHDRAWN)))
                .isInstanceOf(IllegalArgumentException.class);

        application.changeStatus(ApplicationStatus.WITHDRAWN);
        assertThatThrownBy(() -> service.updateStatus(candidateId, jobId,
                new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED)))
                .isInstanceOf(IllegalStateException.class);

        application.changeStatus(ApplicationStatus.APPLIED);
        assertThat(service.updateStatus(candidateId, jobId,
                new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED)).status())
                .isEqualTo(ApplicationStatus.SHORTLISTED.name());
    }

    private Application application() {
        return new Application(jobId, candidateId, candidateId);
    }
}
