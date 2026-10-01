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
import com.talentpipe.job.entity.Job;
import com.talentpipe.job.service.JobService;
import com.talentpipe.job.repository.JobRepository;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final JobService jobService;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public ApplicationService(ApplicationRepository applicationRepository,
                              CandidateRepository candidateRepository,
                              JobService jobService,
                              JobRepository jobRepository,
                              UserRepository userRepository,
                              ApplicationEventPublisher events) {
        this.applicationRepository = applicationRepository;
        this.candidateRepository = candidateRepository;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    @Transactional
    public ApplicationResponse submit(UUID candidateId, UUID jobId) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found");
        }
        Job job = jobService.requirePublished(jobId);
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new DuplicateResourceException("You have already applied to this job");
        }
        Application application = applicationRepository.save(new Application(jobId, candidateId, job.getTenantId()));
        return toResponse(application, job);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forCandidate(UUID candidateId) {
        return applicationRepository.findAllByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream().map(application -> toResponse(application, jobRepository.findById(application.getJobId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getForCandidate(UUID candidateId, UUID applicationId) {
        Application application = applicationRepository.findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return toResponse(application, jobRepository.findById(application.getJobId()).orElse(null));
    }

    @Transactional
    public ApplicationResponse withdraw(UUID candidateId, UUID applicationId) {
        Application application = applicationRepository.findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            return toResponse(application, jobRepository.findById(application.getJobId()).orElse(null));
        }
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Rejected applications cannot be withdrawn");
        }
        application.changeStatus(ApplicationStatus.WITHDRAWN);
        Application saved = applicationRepository.save(application);
        notifyHiringTeam(saved);
        return toResponse(saved, jobRepository.findById(saved.getJobId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forTenant(UUID tenantId) {
        return applicationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream().map(application -> toResponse(application,
                jobRepository.findById(application.getJobId()).orElse(null))).toList();
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID tenantId, UUID applicationId, UpdateApplicationStatusRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .filter(item -> item.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (request.status() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("HR users cannot withdraw a candidate application");
        }
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new IllegalStateException("Withdrawn applications cannot change status");
        }
        application.changeStatus(request.status());
        Application saved = applicationRepository.save(application);
        return toResponse(saved, jobRepository.findById(saved.getJobId()).orElse(null));
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

    private ApplicationResponse toResponse(Application application, Job job) {
        return new ApplicationResponse(application.getId(), application.getJobId(), application.getCandidateId(),
                application.getTenantId(), job == null ? null : job.getTitle(), application.getStatus().name(),
                application.getCreatedAt(), application.getUpdatedAt(), application.getWithdrawnAt());
    }
}
