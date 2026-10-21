package com.talentpipe.application.service;

import com.talentpipe.application.dto.ApplicationResponse;
import com.talentpipe.application.dto.UpdateApplicationStatusRequest;
import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.mapper.JobSlug;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.service.VacancyApplicationGate;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final String APPLICATION_NOT_FOUND = "Application not found";

    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final VacancyApplicationGate vacancyApplicationGate;
    private final JobVacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public ApplicationService(ApplicationRepository applicationRepository,
                              CandidateRepository candidateRepository,
                              VacancyApplicationGate vacancyApplicationGate,
                              JobVacancyRepository vacancyRepository,
                              UserRepository userRepository,
                              ApplicationEventPublisher events) {
        this.applicationRepository = applicationRepository;
        this.candidateRepository = candidateRepository;
        this.vacancyApplicationGate = vacancyApplicationGate;
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    @Transactional
    public ApplicationResponse submit(UUID candidateId, String slugOrId) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found");
        }
        UUID jobId = JobSlug.resolveId(slugOrId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        VacancyApplicationTarget vacancy = vacancyApplicationGate.requireOpen(jobId);
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new DuplicateResourceException("You have already applied to this job");
        }
        Application application = applicationRepository.save(new Application(
                vacancy.vacancyId(), candidateId, vacancy.tenantId()));
        return toResponse(application, vacancy.title());
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forCandidate(UUID candidateId) {
        return applicationRepository.findAllByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream().map(application -> toResponse(application, vacancyTitle(application.getJobId()))).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getForCandidate(UUID candidateId, UUID applicationId) {
        Application application = applicationRepository.findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(APPLICATION_NOT_FOUND));
        return toResponse(application, vacancyTitle(application.getJobId()));
    }

    @Transactional
    public ApplicationResponse withdraw(UUID candidateId, UUID applicationId) {
        Application application = applicationRepository.findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(APPLICATION_NOT_FOUND));
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            return toResponse(application, vacancyTitle(application.getJobId()));
        }
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Rejected applications cannot be withdrawn");
        }
        application.changeStatus(ApplicationStatus.WITHDRAWN);
        Application saved = applicationRepository.save(application);
        notifyHiringTeam(saved);
        return toResponse(saved, vacancyTitle(saved.getJobId()));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forTenant(UUID tenantId) {
        return applicationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream().map(application -> toResponse(application,
                vacancyTitle(application.getJobId()))).toList();
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID tenantId, UUID applicationId, UpdateApplicationStatusRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .filter(item -> item.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException(APPLICATION_NOT_FOUND));
        if (request.status() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("HR users cannot withdraw a candidate application");
        }
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalStateException("Withdrawn applications cannot change status");
        }
        application.changeStatus(request.status());
        Application saved = applicationRepository.save(application);
        return toResponse(saved, vacancyTitle(saved.getJobId()));
    }

    private void notifyHiringTeam(Application application) {
        List<User> recipients = userRepository.findAllByTenantIdAndRoleNameIn(
                application.getTenantId(), List.of(RoleName.COMPANY_ADMIN, RoleName.HR_MANAGER));
        for (User recipient : recipients) {
            events.publishEvent(NotificationRequestedEvent.forUser(
                    NotificationType.APPLICATION_WITHDRAWN,
                    application.getTenantId(), recipient.getId(), recipient.getEmail(), "",
                    recipient.getFirstName() + " " + recipient.getLastName()));
        }
    }

    private String vacancyTitle(UUID vacancyId) {
        return vacancyRepository.findById(vacancyId).map(JobVacancy::getTitle).orElse(null);
    }

    private ApplicationResponse toResponse(Application application, String jobTitle) {
        return new ApplicationResponse(application.getId(), application.getJobId(), application.getCandidateId(),
                application.getTenantId(), jobTitle, application.getStatus().name(),
                application.getCreatedAt(), application.getUpdatedAt(), application.getWithdrawnAt());
    }
}
