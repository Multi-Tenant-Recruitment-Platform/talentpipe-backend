package com.talentpipe.job.service;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobFilterOptionsResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobSlug;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.PublicJobSearchCriteria;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.service.TenantService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The candidate portal's view of vacancies (PB-017): browse, search, filter,
 * and read one advert. Unauthenticated and cross-tenant.
 *
 * <p><strong>The one rule here:</strong> only PUBLISHED vacancies exist as far
 * as this class is concerned. Every query it runs carries that condition, and
 * a vacancy in any other state answers 404 — a draft must not be discoverable
 * by guessing its id, and a closed vacancy is off the portal.</p>
 *
 * <p><strong>Nothing is cached</strong>, deliberately. Each request reads the
 * live table, which is what makes a publish, an edit or a close show up on the
 * portal with the very next request rather than after some expiry. The partial
 * indexes in V12 are what keep that affordable.</p>
 */
@Service
public class PublicJobService {

    /** The largest page of adverts the board serves in one request. */
    static final int MAX_PAGE_SIZE = 50;

    /** How many choices each filter offers; a long tail beyond this is reached by typing instead. */
    static final int MAX_FILTER_OPTIONS = 100;

    private final JobVacancyRepository vacancyRepository;
    private final TenantService tenantService;
    private final VacancyCalendar calendar;
    private final JobVacancyMapper mapper;

    public PublicJobService(JobVacancyRepository vacancyRepository,
                            TenantService tenantService,
                            VacancyCalendar calendar,
                            JobVacancyMapper mapper) {
        this.vacancyRepository = vacancyRepository;
        this.tenantService = tenantService;
        this.calendar = calendar;
        this.mapper = mapper;
    }

    /**
     * Published vacancies matching the given keyword and filters, a page at a
     * time. Every argument is optional and they combine with AND.
     *
     * @param keyword  matched against title, skills, summary and description;
     *                 results are ranked by relevance when present
     * @param category the vacancy's department, matched whole, ignoring case
     * @param location matched anywhere in the vacancy's location, ignoring case
     */
    @Transactional(readOnly = true)
    public Page<JobSummaryResponse> search(String keyword, String category, String location,
                                           int page, int size) {
        PublicJobSearchCriteria criteria = PublicJobSearchCriteria.of(keyword, category, location);
        Pageable pageable = Paging.of(page, size, MAX_PAGE_SIZE, Sort.unsorted());

        // TODO(sprint3): leave out vacancies of SUSPENDED/CANCELLED tenants once
        // the platform can actually suspend a tenant.
        Page<JobVacancy> vacancies = vacancyRepository.searchPublished(criteria, pageable);
        Map<UUID, CompanySummaryResponse> companies = companiesOf(vacancies.getContent());
        return vacancies.map(vacancy -> summarize(vacancy, companies.get(vacancy.getTenantId())));
    }

    /**
     * One published advert, by the key in its public URL.
     *
     * @param slugOrId a slug from {@link JobSlug} or a plain vacancy id
     * @throws ResourceNotFoundException (404) if it does not resolve to a
     *         vacancy that is currently published
     */
    @Transactional(readOnly = true)
    public JobDetailResponse getPublished(String slugOrId) {
        JobVacancy vacancy = JobSlug.resolveId(slugOrId)
                .flatMap(id -> vacancyRepository.findByIdAndStatus(id, VacancyStatus.PUBLISHED))
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        CompanySummaryResponse company = companiesOf(List.of(vacancy)).get(vacancy.getTenantId());
        return mapper.toDetail(vacancy, company, isAcceptingApplications(vacancy, company));
    }

    /** The categories and locations the board can currently be filtered by. */
    @Transactional(readOnly = true)
    public JobFilterOptionsResponse filterOptions() {
        Pageable top = PageRequest.of(0, MAX_FILTER_OPTIONS);
        return new JobFilterOptionsResponse(
                vacancyRepository.findPublishedCategories(top),
                vacancyRepository.findPublishedLocations(top));
    }

    // ---------------------------------------------------------------- private

    private JobSummaryResponse summarize(JobVacancy vacancy, CompanySummaryResponse company) {
        return mapper.toSummary(vacancy, company, isAcceptingApplications(vacancy, company));
    }

    /** The deadline is judged against today's date where the hiring company is. */
    private boolean isAcceptingApplications(JobVacancy vacancy, CompanySummaryResponse company) {
        return vacancy.isAcceptingApplications(calendar.today(company == null ? null : company.timezone()));
    }

    /**
     * The companies behind a page of vacancies, fetched in one call. The job
     * module does not join the tenants table: company data belongs to the
     * tenant module and is asked for through its service.
     */
    private Map<UUID, CompanySummaryResponse> companiesOf(Collection<JobVacancy> vacancies) {
        Set<UUID> tenantIds = vacancies.stream().map(JobVacancy::getTenantId).collect(Collectors.toSet());
        return tenantService.findCompanySummaries(tenantIds);
    }
}
