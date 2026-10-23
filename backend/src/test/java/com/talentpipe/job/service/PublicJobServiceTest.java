package com.talentpipe.job.service;

import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobFilterOption;
import com.talentpipe.job.dto.JobFilterOptionsResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WorkplaceType;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.BoardFacetCount;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.PublicJobSearchCriteria;
import com.talentpipe.job.repository.SalaryRangeRow;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.service.TenantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link PublicJobService}. The SQL behind the search is
 * covered against a real database in {@code PublicJobSearchIntegrationTest};
 * these tests cover what the service adds around it.
 */
@ExtendWith(MockitoExtension.class)
class PublicJobServiceTest {

    private static final UUID ACME_ID = UUID.randomUUID();
    private static final UUID GLOBEX_ID = UUID.randomUUID();
    private static final CompanySummaryResponse ACME =
            new CompanySummaryResponse(ACME_ID, "Acme Corp", "acme", "https://cdn.example.com/acme.png", "Asia/Colombo");
    private static final CompanySummaryResponse GLOBEX =
            new CompanySummaryResponse(GLOBEX_ID, "Globex", "globex", null, null);

    @Mock
    private JobVacancyRepository repository;

    @Mock
    private TenantService tenantService;

    @Mock
    private VacancyCalendar calendar;

    @Captor
    private ArgumentCaptor<PublicJobSearchCriteria> criteria;

    @Captor
    private ArgumentCaptor<Pageable> pageable;

    private PublicJobService service;

    @BeforeEach
    void setUp() {
        service = new PublicJobService(repository, tenantService, calendar, new JobVacancyMapper());
        lenient().when(calendar.today(anyString())).thenReturn(TODAY);
        lenient().when(calendar.today((String) isNull())).thenReturn(TODAY);
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_listsEachVacancyWithItsOwnCompanysBranding() {
        JobVacancy atAcme = VacancyFixtures.published(ACME_ID);
        JobVacancy atGlobex = VacancyFixtures.published(GLOBEX_ID);
        when(repository.searchPublished(any(), any())).thenReturn(page(atAcme, atGlobex));
        when(tenantService.findCompanySummaries(Set.of(ACME_ID, GLOBEX_ID)))
                .thenReturn(Map.of(ACME_ID, ACME, GLOBEX_ID, GLOBEX));

        Page<JobSummaryResponse> result = service.search(PublicJobSearchCriteria.none(), 0, 20);

        assertThat(result.getContent()).extracting(JobSummaryResponse::companyName)
                .containsExactly("Acme Corp", "Globex");
        assertThat(result.getContent().get(0).companyLogoUrl()).isEqualTo("https://cdn.example.com/acme.png");
        assertThat(result.getContent().get(1).companyLogoUrl()).isNull();
    }

    @Test
    void search_fetchesCompaniesOncePerPage_notOncePerVacancy() {
        when(repository.searchPublished(any(), any())).thenReturn(page(
                VacancyFixtures.published(ACME_ID),
                VacancyFixtures.published(ACME_ID),
                VacancyFixtures.published(GLOBEX_ID)));
        when(tenantService.findCompanySummaries(any())).thenReturn(Map.of(ACME_ID, ACME, GLOBEX_ID, GLOBEX));

        service.search(PublicJobSearchCriteria.none(), 0, 20);

        verify(tenantService, times(1)).findCompanySummaries(Set.of(ACME_ID, GLOBEX_ID));
    }

    @Test
    void search_passesNormalizedCriteriaToTheRepository() {
        when(repository.searchPublished(any(), any())).thenReturn(Page.empty());

        PublicJobSearchCriteria wanted = PublicJobSearchCriteria.of("  senior   java ", " Engineering ", "");

        service.search(wanted, 0, 20);

        verify(repository).searchPublished(criteria.capture(), any());
        assertThat(criteria.getValue()).isEqualTo(PublicJobSearchCriteria.of("senior java", "Engineering", null));
    }

    @Test
    void search_capsThePageSizeAtFifty() {
        when(repository.searchPublished(any(), any())).thenReturn(Page.empty());

        service.search(PublicJobSearchCriteria.none(), 0, 10_000);

        verify(repository).searchPublished(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void search_keepsThePagingMetadataOfTheQuery() {
        JobVacancy vacancy = VacancyFixtures.published(ACME_ID);
        when(repository.searchPublished(any(), any()))
                .thenReturn(new PageImpl<>(List.of(vacancy), PageRequest.of(2, 1), 5));
        when(tenantService.findCompanySummaries(any())).thenReturn(Map.of(ACME_ID, ACME));

        Page<JobSummaryResponse> result = service.search(PublicJobSearchCriteria.none(), 2, 1);

        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(5);
    }

    @Test
    void search_marksAVacancyPastItsDeadlineAsNotAcceptingApplications() {
        JobVacancy vacancy = VacancyFixtures.published(ACME_ID);
        when(repository.searchPublished(any(), any())).thenReturn(page(vacancy));
        when(tenantService.findCompanySummaries(any())).thenReturn(Map.of(ACME_ID, ACME));
        // In the company's own timezone the deadline was yesterday.
        when(calendar.today("Asia/Colombo")).thenReturn(vacancy.getApplicationDeadline().plusDays(1));

        Page<JobSummaryResponse> result = service.search(PublicJobSearchCriteria.none(), 0, 20);

        assertThat(result.getContent().get(0).acceptingApplications()).isFalse();
    }

    @Test
    void search_stillListsAVacancyWhoseCompanyCannotBeResolved() {
        when(repository.searchPublished(any(), any())).thenReturn(page(VacancyFixtures.published(ACME_ID)));
        when(tenantService.findCompanySummaries(any())).thenReturn(Map.of());

        Page<JobSummaryResponse> result = service.search(PublicJobSearchCriteria.none(), 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).companyName()).isEmpty();
    }

    // ------------------------------------------------------------------ detail

    @Test
    void getPublished_resolvesTheSlugAndReturnsTheFullAdvert() {
        JobVacancy vacancy = VacancyFixtures.published(ACME_ID);
        when(repository.findByIdAndStatus(vacancy.getId(), VacancyStatus.PUBLISHED)).thenReturn(Optional.of(vacancy));
        when(tenantService.findCompanySummaries(Set.of(ACME_ID))).thenReturn(Map.of(ACME_ID, ACME));
        String slug = "senior-backend-engineer-acme-corp-" + vacancy.getId().toString().replace("-", "");

        JobDetailResponse detail = service.getPublished(slug);

        assertThat(detail.summary().id()).isEqualTo(vacancy.getId());
        assertThat(detail.summary().companyName()).isEqualTo("Acme Corp");
        assertThat(detail.jobDescription()).isEqualTo(vacancy.content().jobDescription());
        assertThat(detail.summary().acceptingApplications()).isTrue();
    }

    @Test
    void getPublished_alsoAcceptsAPlainId() {
        JobVacancy vacancy = VacancyFixtures.published(ACME_ID);
        when(repository.findByIdAndStatus(vacancy.getId(), VacancyStatus.PUBLISHED)).thenReturn(Optional.of(vacancy));
        when(tenantService.findCompanySummaries(any())).thenReturn(Map.of(ACME_ID, ACME));

        assertThat(service.getPublished(vacancy.getId().toString()).summary().id()).isEqualTo(vacancy.getId());
    }

    @Test
    void getPublished_aVacancyThatIsNotPublished_isNotFound() {
        // The repository is asked for PUBLISHED only, so a draft, closed or
        // archived vacancy with this id simply does not come back.
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndStatus(id, VacancyStatus.PUBLISHED)).thenReturn(Optional.empty());
        String key = id.toString();

        assertThatThrownBy(() -> service.getPublished(key))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job not found");
    }

    @Test
    void getPublished_anUnparseableKey_isNotFound_withoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.getPublished("not-a-real-slug"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).findByIdAndStatus(any(), eq(VacancyStatus.PUBLISHED));
    }

    // ----------------------------------------------------------------- filters

    @Test
    void filterOptions_returnsEveryFacet_boundedInNumber() {
        List<JobFilterOption> categories = List.of(new JobFilterOption("Engineering", 7));
        List<JobFilterOption> locations = List.of(new JobFilterOption("Colombo, Sri Lanka", 5));
        when(repository.findPublishedCategories(any())).thenReturn(categories);
        when(repository.findPublishedLocations(any())).thenReturn(locations);
        when(repository.countPublishedByEmploymentType()).thenReturn(List.of(facet(EmploymentType.FULL_TIME, 6)));
        when(repository.countPublishedByWorkplaceType()).thenReturn(List.of(facet(WorkplaceType.REMOTE, 3)));
        when(repository.findPublishedSalaryRanges()).thenReturn(List.of(salaryRange()));

        JobFilterOptionsResponse options = service.filterOptions();

        assertThat(options.categories()).isEqualTo(categories);
        assertThat(options.locations()).isEqualTo(locations);
        assertThat(options.employmentTypes()).containsExactly(new JobFilterOption("FULL_TIME", 6));
        assertThat(options.workplaceTypes()).containsExactly(new JobFilterOption("REMOTE", 3));
        assertThat(options.salaryRanges()).containsExactly(new JobFilterOptionsResponse.SalaryRange(
                PayPeriod.MONTHLY, "LKR", new BigDecimal("100000"), new BigDecimal("250000"), 4));
        verify(repository).findPublishedCategories(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(PublicJobService.MAX_FILTER_OPTIONS);
    }

    private static Page<JobVacancy> page(JobVacancy... vacancies) {
        return new PageImpl<>(List.of(vacancies));
    }

    private static <E extends Enum<E>> BoardFacetCount<E> facet(E value, long total) {
        return new BoardFacetCount<>() {
            @Override
            public E getFacet() {
                return value;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static SalaryRangeRow salaryRange() {
        return new SalaryRangeRow() {
            @Override
            public String getPayPeriod() {
                return "MONTHLY";
            }

            @Override
            public String getCurrency() {
                return "LKR";
            }

            @Override
            public BigDecimal getLowest() {
                return new BigDecimal("100000");
            }

            @Override
            public BigDecimal getHighest() {
                return new BigDecimal("250000");
            }

            @Override
            public long getTotal() {
                return 4;
            }
        };
    }
}
