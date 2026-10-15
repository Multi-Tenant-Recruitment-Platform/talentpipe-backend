package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.repository.JobVacancyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single question the applications module must ask before it accepts an
 * application: <em>is this vacancy open?</em> (PB-020 — "closed vacancies stop
 * accepting new applications".)
 *
 * <p>This is the job module's side of that rule, and its public API for it.
 * The applications module calls {@link #requireOpen} at the top of its submit
 * use case and stores the application against the returned
 * {@link VacancyApplicationTarget}; it never reads a vacancy's status itself,
 * so what "open" means is decided in exactly one place.</p>
 *
 * <h3>Call it inside the submitting transaction</h3>
 * <p>{@code requireOpen} takes a shared lock on the vacancy row that is held
 * until the caller's transaction ends. Without it there is a window: the gate
 * reads PUBLISHED, a recruiter closes the vacancy, and the application is then
 * inserted against a vacancy that is already closed. With it, a close waits
 * for in-flight applications and every later application sees CLOSED.
 * Applicants never block each other — the lock is shared.</p>
 *
 * <p>The lock requires a read-write transaction; do not call this from a
 * {@code readOnly} one. For a non-binding hint (whether to show an Apply
 * button) use {@code acceptingApplications} on the public job DTOs instead.</p>
 */
@Service
public class VacancyApplicationGate {

    private final JobVacancyRepository vacancyRepository;
    private final VacancyCalendar calendar;

    public VacancyApplicationGate(JobVacancyRepository vacancyRepository, VacancyCalendar calendar) {
        this.vacancyRepository = vacancyRepository;
        this.calendar = calendar;
    }

    /**
     * Confirms the vacancy can take an application right now.
     *
     * @return what the applications module needs in order to record it
     * @throws ResourceNotFoundException (404) if there is no such vacancy, or
     *         it is still a draft — a draft was never public, so to a
     *         candidate it must look exactly like a vacancy that does not exist
     * @throws BusinessRuleException     (422) if the vacancy has been closed
     *         or archived, or its application deadline has passed
     */
    @Transactional
    public VacancyApplicationTarget requireOpen(UUID vacancyId) {
        JobVacancy vacancy = vacancyRepository.findByIdForShare(vacancyId)
                .filter(found -> found.getStatus() != VacancyStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (vacancy.getStatus() != VacancyStatus.PUBLISHED) {
            throw new BusinessRuleException("This vacancy is closed and no longer accepts applications.");
        }
        if (!vacancy.isAcceptingApplications(calendar.today(vacancy.getTenantId()))) {
            throw new BusinessRuleException(
                    "Applications for this vacancy closed on " + vacancy.getApplicationDeadline() + ".");
        }
        return new VacancyApplicationTarget(vacancy.getId(), vacancy.getTenantId(), vacancy.getTitle());
    }
}
