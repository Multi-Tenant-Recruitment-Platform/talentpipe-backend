package com.talentpipe.job.repository;

import com.talentpipe.job.dto.JobFilterOption;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for vacancies — private to the job module by convention.
 *
 * <p>Two audiences read this table and they are scoped differently:</p>
 * <ul>
 *   <li><strong>Recruiters</strong> — every query takes the tenant id from the
 *       access token as part of its key. A vacancy in another tenant is not
 *       "forbidden", it simply does not match, and the caller answers 404.</li>
 *   <li><strong>Candidates</strong> — cross-tenant by nature, but only ever
 *       over PUBLISHED rows. The keyword search lives in
 *       {@link JobVacancySearchRepository}.</li>
 * </ul>
 */
public interface JobVacancyRepository extends JpaRepository<JobVacancy, UUID>, JobVacancySearchRepository {

    // ------------------------------------------------------- recruiter side

    /** The only way recruiter-facing code loads one vacancy: by id <em>and</em> owning tenant. */
    Optional<JobVacancy> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Every vacancy in the tenant, archived included. */
    Page<JobVacancy> findByTenantId(UUID tenantId, Pageable pageable);

    /** The tenant's vacancies in any of the given states. */
    Page<JobVacancy> findByTenantIdAndStatusIn(UUID tenantId, Collection<VacancyStatus> statuses,
                                               Pageable pageable);

    /**
     * How many vacancies the tenant has in each state. A state with none is
     * simply absent from the result; the service fills in the zeros.
     */
    @Query("""
            select v.status as status, count(v) as total
            from JobVacancy v
            where v.tenantId = :tenantId
            group by v.status
            """)
    List<VacancyStatusCount> countByStatus(@Param("tenantId") UUID tenantId);

    // ------------------------------------------------------- candidate side

    /** One vacancy, only if it is in the given state — the public detail page asks for PUBLISHED. */
    Optional<JobVacancy> findByIdAndStatus(UUID id, VacancyStatus status);

    /**
     * Loads a vacancy holding a shared row lock ({@code SELECT … FOR SHARE})
     * until the surrounding transaction ends.
     *
     * <p>For the application gate. Any number of applicants can hold the lock
     * at once, so they never wait on each other; a recruiter's close, which
     * updates the row, waits for them and they in turn see its result. That
     * closes the window in which an application could be accepted a moment
     * after the vacancy was closed.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select v from JobVacancy v where v.id = :id")
    Optional<JobVacancy> findByIdForShare(@Param("id") UUID id);

    /**
     * The categories (departments) candidates can filter the board by, with
     * how many published vacancies each holds, most populated first.
     *
     * <p>Grouped case-insensitively so "Engineering" and "engineering" from
     * two companies are one option; the spelling shown is the first in sort
     * order among those entered.</p>
     */
    @Query("""
            select new com.talentpipe.job.dto.JobFilterOption(min(v.department), count(v))
            from JobVacancy v
            where v.status = com.talentpipe.job.entity.VacancyStatus.PUBLISHED and v.department <> ''
            group by lower(v.department)
            order by count(v) desc, min(v.department) asc
            """)
    List<JobFilterOption> findPublishedCategories(Pageable limit);

    /** The locations candidates can filter the board by; same shape as {@link #findPublishedCategories}. */
    @Query("""
            select new com.talentpipe.job.dto.JobFilterOption(min(v.location), count(v))
            from JobVacancy v
            where v.status = com.talentpipe.job.entity.VacancyStatus.PUBLISHED and v.location <> ''
            group by lower(v.location)
            order by count(v) desc, min(v.location) asc
            """)
    List<JobFilterOption> findPublishedLocations(Pageable limit);
}
