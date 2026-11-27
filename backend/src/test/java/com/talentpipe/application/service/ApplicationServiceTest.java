package com.talentpipe.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.application.dto.UpdateApplicationStatusRequest;
import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.candidate.service.CandidateCvService;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.service.VacancyAccess;
import com.talentpipe.job.service.VacancyApplicationGate;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import com.talentpipe.security.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private CandidateCvService candidateCvService;
    @Mock private VacancyApplicationGate vacancyApplicationGate;
    @Mock private JobVacancyRepository vacancyRepository;
    @Mock private VacancyAccess vacancyAccess;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private ApplicationService service;
    private UUID candidateId;
    private UUID jobId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, candidateRepository, candidateCvService,
                vacancyApplicationGate, vacancyRepository, vacancyAccess, userRepository, events);
        candidateId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        memberId = UUID.randomUUID();
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
    void submit_keepsTheCvThatIsActiveNow() {
        UUID activeCv = UUID.randomUUID();
        when(candidateCvService.findActiveCvId(candidateId)).thenReturn(Optional.of(activeCv));

        assertThat(submitted().getCvId()).isEqualTo(activeCv);
    }

    @Test
    void submit_withoutACv_isStillAccepted() {
        when(candidateCvService.findActiveCvId(candidateId)).thenReturn(Optional.empty());

        assertThat(submitted().getCvId()).isNull();
    }

    /** Submits a valid application and returns it as it was saved. */
    private Application submitted() {
        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(vacancyApplicationGate.requireOpen(jobId))
                .thenReturn(new VacancyApplicationTarget(jobId, UUID.randomUUID(), "Backend Engineer"));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(candidateId, jobId.toString());

        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(saved.capture());
        return saved.getValue();
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
        assertThat(service.forTenant(seesAll())).isEmpty();
    }

    @Test
    void tenantList_withoutViewAll_isOnlyTheVacanciesTheMemberWorksOn() {
        UUID theirVacancy = UUID.randomUUID();
        when(vacancyAccess.vacancyIdsWorkedOnBy(candidateId, memberId)).thenReturn(Set.of(theirVacancy));
        when(applicationRepository.findAllByTenantIdAndJobIdInOrderByCreatedAtDesc(candidateId, Set.of(theirVacancy)))
                .thenReturn(List.of(new Application(theirVacancy, UUID.randomUUID(), candidateId, null)));

        assertThat(service.forTenant(scoped())).extracting("jobId").containsExactly(theirVacancy);
        verify(applicationRepository, never()).findAllByTenantIdOrderByCreatedAtDesc(any());
    }

    @Test
    void tenantList_withoutViewAll_andNoVacancies_isEmptyWithoutAQuery() {
        when(vacancyAccess.vacancyIdsWorkedOnBy(candidateId, memberId)).thenReturn(Set.of());

        assertThat(service.forTenant(scoped())).isEmpty();
        verify(applicationRepository, never()).findAllByTenantIdAndJobIdInOrderByCreatedAtDesc(any(), anyCollection());
        verify(applicationRepository, never()).findAllByTenantIdOrderByCreatedAtDesc(any());
    }

    @Test
    void updateStatus_withoutViewAll_onAVacancyTheyDoNotWorkOn_isNotFound_andChangesNothing() {
        Application application = application();
        when(applicationRepository.findById(jobId)).thenReturn(Optional.of(application));
        when(vacancyAccess.worksOn(candidateId, memberId, jobId)).thenReturn(false);
        UpdateApplicationStatusRequest reject = new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED);
        ApplicantViewer viewer = scoped();

        assertThatThrownBy(() -> service.updateStatus(viewer, jobId, reject))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void updateStatus_withoutViewAll_onTheirOwnVacancy_isAllowed() {
        Application application = application();
        when(applicationRepository.findById(jobId)).thenReturn(Optional.of(application));
        when(vacancyAccess.worksOn(candidateId, memberId, jobId)).thenReturn(true);
        when(applicationRepository.save(application)).thenReturn(application);

        assertThat(service.updateStatus(scoped(), jobId,
                new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED)).status())
                .isEqualTo(ApplicationStatus.SHORTLISTED.name());
    }

    @Test
    void updateStatus_onAnotherCompanysApplicant_isNotFound() {
        when(applicationRepository.findById(jobId)).thenReturn(Optional.of(application()));
        ApplicantViewer otherCompany = new ApplicantViewer(UUID.randomUUID(), memberId, true);
        UpdateApplicationStatusRequest reject = new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED);

        assertThatThrownBy(() -> service.updateStatus(otherCompany, jobId, reject))
                .isInstanceOf(ResourceNotFoundException.class);
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
        when(userRepository.findActiveMembersHolding(any(), any())).thenReturn(List.of());

        assertThat(service.withdraw(candidateId, jobId).status())
                .isEqualTo(ApplicationStatus.WITHDRAWN.name());
        verify(applicationRepository).save(application);
    }

    @Test
    void withdraw_notifiesTheMembersWhoSeeEveryApplicant_notARoleName() {
        Application application = application();
        when(applicationRepository.findByIdAndCandidateId(jobId, candidateId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);
        User recruiter = new User(candidateId, null, null, "recruiter@example.com", "hash", "Rae", "Cruiter",
                UserStatus.ACTIVE);
        when(userRepository.findActiveMembersHolding(candidateId, Permission.Keys.APPLICATIONS_VIEW_ALL))
                .thenReturn(List.of(recruiter));

        service.withdraw(candidateId, jobId);

        ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(events).publishEvent(sent.capture());
        assertThat(sent.getValue().recipient()).isEqualTo("recruiter@example.com");
    }

    @Test
    void updateStatusRejectsInvalidTransitionsAndUpdatesActiveApplication() {
        Application application = application();
        when(applicationRepository.findById(jobId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);
        UpdateApplicationStatusRequest withdrawRequest =
                new UpdateApplicationStatusRequest(ApplicationStatus.WITHDRAWN);
        UpdateApplicationStatusRequest rejectRequest =
                new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED);
        UpdateApplicationStatusRequest shortlistRequest =
                new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED);

        ApplicantViewer viewer = seesAll();
        assertThatThrownBy(() -> service.updateStatus(viewer, jobId,
                withdrawRequest))
                .isInstanceOf(IllegalArgumentException.class);

        application.changeStatus(ApplicationStatus.WITHDRAWN);
        assertThatThrownBy(() -> service.updateStatus(viewer, jobId,
                rejectRequest))
                .isInstanceOf(IllegalStateException.class);

        application.changeStatus(ApplicationStatus.APPLIED);
        assertThat(service.updateStatus(viewer, jobId, shortlistRequest).status())
                .isEqualTo(ApplicationStatus.SHORTLISTED.name());
    }

    /** Fixture applications belong to the tenant {@code candidateId}, to vacancy {@code jobId}. */
    private Application application() {
        return new Application(jobId, candidateId, candidateId, null);
    }

    /** A member of the fixture tenant whose role holds applications.view.all. */
    private ApplicantViewer seesAll() {
        return new ApplicantViewer(candidateId, memberId, true);
    }

    /** A member of the fixture tenant who sees only applicants to vacancies they work on. */
    private ApplicantViewer scoped() {
        return new ApplicantViewer(candidateId, memberId, false);
    }
}
