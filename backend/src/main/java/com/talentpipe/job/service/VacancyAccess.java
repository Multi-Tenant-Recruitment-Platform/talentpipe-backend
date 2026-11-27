package com.talentpipe.job.service;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.repository.JobVacancyRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one way recruiter-side code loads a single vacancy.
 *
 * <p>A vacancy is found only if it belongs to the viewer's company and the
 * viewer may see it: everyone with {@code jobs.view.all}, otherwise only the
 * people who work on it. Anything else answers 404 — a vacancy someone may
 * not see is indistinguishable from one that does not exist, inside a company
 * as well as across companies.</p>
 */
@Component
public class VacancyAccess {

    private final JobVacancyRepository vacancyRepository;

    public VacancyAccess(JobVacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    /** @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see */
    public JobVacancy require(VacancyViewer viewer, UUID vacancyId) {
        Optional<JobVacancy> vacancy = viewer.seesAllVacancies()
                ? vacancyRepository.findByIdAndTenantId(vacancyId, viewer.tenantId())
                : vacancyRepository.findWorkedOnBy(vacancyId, viewer.tenantId(), viewer.userId());
        return vacancy.orElseThrow(() -> new ResourceNotFoundException("Vacancy not found"));
    }

    /**
     * The vacancies a member works on (created, recruits for, manages, or is
     * on the hiring team or an interview panel of), by id. For other modules
     * that scope their own data to the same rule, such as applicants.
     */
    public Set<UUID> vacancyIdsWorkedOnBy(UUID tenantId, UUID userId) {
        return Set.copyOf(vacancyRepository.findIdsWorkedOnBy(tenantId, userId));
    }

    /** Whether the member works on this vacancy of their company. */
    public boolean worksOn(UUID tenantId, UUID userId, UUID vacancyId) {
        return vacancyRepository.findWorkedOnBy(vacancyId, tenantId, userId).isPresent();
    }
}
