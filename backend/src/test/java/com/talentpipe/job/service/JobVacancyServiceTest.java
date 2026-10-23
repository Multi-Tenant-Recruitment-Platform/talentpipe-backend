package com.talentpipe.job.service;

import static com.talentpipe.job.VacancyFixtures.NOW;
import static com.talentpipe.job.VacancyFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.VacancyFeaturesRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.validator.JobVacancyMemberValidator;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import com.talentpipe.job.validator.JobVacancyShapeValidator;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.VacancyStatusCount;
import com.talentpipe.tenant.dto.TenantSettingsResponse;
import com.talentpipe.tenant.service.TenantService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link JobVacancyService}. Pure Mockito — no Spring context.
 *
 * <p>Only the edges are mocked: the repository, the clock and the user
 * directory. The normalizer, the content rules, the mapper and the entity are
 * the real ones, so a test here exercises a use case the way production runs
 * it, state machine included.</p>
 */
@ExtendWith(MockitoExtension.class)
class JobVacancyServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    /** An Owner or Recruiter: sees every vacancy in the company. */
    private static final VacancyViewer VIEWER = new VacancyViewer(TENANT, ACTOR, true);

    /** Someone whose role lacks jobs.view.all: sees only the vacancies they work on. */
    private static final VacancyViewer WORKER = new VacancyViewer(TENANT, ACTOR, false);

    @Mock
    private JobVacancyRepository repository;

    @Mock
    private VacancyCalendar calendar;

    @Mock
    private UserDirectoryService userDirectory;

    @Mock
    private VacancyHiringTeamService hiringTeam;

    @Mock
    private VacancyIndustryPolicy industryPolicy;

    @Mock
    private TenantService tenantService;

    @Captor
    private ArgumentCaptor<JobVacancy> savedVacancy;

    @Captor
    private ArgumentCaptor<Pageable> pageable;

    private JobVacancyService service;

    @BeforeEach
    void setUp() {
        service = new JobVacancyService(
                repository,
                new VacancyAccess(repository),
                hiringTeam,
                industryPolicy,
                new VacancyContentNormalizer(new HtmlSanitizer()),
                new JobVacancyShapeValidator(),
                new JobVacancyPublishValidator(),
                new JobVacancyMemberValidator(userDirectory),
                calendar,
                userDirectory,
                tenantService,
                new JobVacancyMapper());

        lenient().when(calendar.now()).thenReturn(NOW);
        lenient().when(calendar.today(TENANT)).thenReturn(TODAY);
        lenient().when(tenantService.getSettings(TENANT)).thenReturn(companySwitches(false, false));
        // Behave like a real save: hand the entity back, with an id if it is new.
        lenient().when(repository.saveAndFlush(any(JobVacancy.class))).thenAnswer(invocation -> {
            JobVacancy vacancy = invocation.getArgument(0);
            return vacancy.getId() == null ? VacancyFixtures.persisted(vacancy) : vacancy;
        });
    }

    private JobVacancy stored(JobVacancy vacancy) {
        lenient().when(repository.findByIdAndTenantId(vacancy.getId(), TENANT)).thenReturn(Optional.of(vacancy));
        return vacancy;
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_asDraft_storesADraftForTheCallersTenant() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        verify(repository).saveAndFlush(savedVacancy.capture());
        assertThat(savedVacancy.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
    }

    @Test
    void create_asDraft_acceptsAnEmptyVacancy_aDraftIsNeverValidatedForCompleteness() {
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.title()).isEmpty();
    }

    @Test
    void create_asPublished_goesLiveInTheSameRequestWithPublishedAtSet() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.PUBLISHED, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void create_asPublished_whenIncomplete_isRefusedAndNothingIsStored() {
        JobVacancyRequest request = VacancyFixtures.complete().jobSummary("")
                .toRequest(VacancyStatus.PUBLISHED, null);

        assertThatThrownBy(() -> service.create(VIEWER, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job summary.");

        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = VacancyStatus.class, names = {"CLOSED", "ARCHIVED"})
    void create_inAnyOtherStatus_isABadRequest(VacancyStatus status) {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(status, null);

        assertThatThrownBy(() -> service.create(VIEWER, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A new vacancy can only be saved as a draft or published.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_storesNormalizedContent() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .title("  <b>Senior</b>   Engineer ").requiredSkills(List.of("Java", "java", " "))
                .toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.title()).isEqualTo("Senior Engineer");
        assertThat(response.requiredSkills()).containsExactly("Java");
    }

    @Test
    void create_markupOnlyTitle_cannotBePublished_becauseItNormalizesToNothing() {
        JobVacancyRequest request = VacancyFixtures.complete().title("<b></b>")
                .toRequest(VacancyStatus.PUBLISHED, null);

        assertThatThrownBy(() -> service.create(VIEWER, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job title.");
    }

    @Test
    void create_withARecruiterFromOutsideTheWorkspace_isRefused() {
        UUID outsider = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, outsider)).thenReturn(false);
        JobVacancyRequest request = VacancyFixtures.complete().assignedRecruiterId(outsider)
                .toRequest(VacancyStatus.DRAFT, null);

        assertThatThrownBy(() -> service.create(VIEWER, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be an active member of your workspace");

        verify(repository, never()).saveAndFlush(any());
    }

    // -------------------------------------------------------------------- read

    @Test
    void get_returnsTheCallersOwnVacancy() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));

        assertThat(service.get(VIEWER, vacancy.getId()).id()).isEqualTo(vacancy.getId());
    }

    @Test
    void get_anotherTenantsVacancy_isNotFound_indistinguishableFromMissing() {
        UUID vacancyId = stored(VacancyFixtures.draft(TENANT)).getId();
        when(repository.findByIdAndTenantId(vacancyId, OTHER_TENANT)).thenReturn(Optional.empty());

        VacancyViewer outsider = new VacancyViewer(OTHER_TENANT, ACTOR, true);

        assertThatThrownBy(() -> service.get(outsider, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vacancy not found");
    }

    @Test
    void get_aVacancyTheWorkerIsNotOn_isNotFound_soItsExistenceStaysHidden() {
        UUID vacancyId = UUID.randomUUID();
        when(repository.findWorkedOnBy(vacancyId, TENANT, ACTOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(WORKER, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vacancy not found");
        verify(repository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void get_aVacancyTheWorkerIsOn_isReturned() {
        JobVacancy vacancy = VacancyFixtures.draft(TENANT);
        when(repository.findWorkedOnBy(vacancy.getId(), TENANT, ACTOR)).thenReturn(Optional.of(vacancy));

        assertThat(service.get(WORKER, vacancy.getId()).id()).isEqualTo(vacancy.getId());
    }

    // -------------------------------------------------------------------- list

    @Test
    void list_withNoFilter_returnsEveryVacancy_archivedIncluded_newestFirst() {
        JobVacancy archived = VacancyFixtures.archived(TENANT);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(VacancyFixtures.draft(TENANT), archived)));

        Page<JobVacancyResponse> page = service.list(VIEWER, VacancyListFilter.none(), 0, 20);

        assertThat(page.getContent()).extracting(JobVacancyResponse::status)
                .containsExactly(VacancyStatus.DRAFT, VacancyStatus.ARCHIVED);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void statusFilter_active_selectsEveryStateExceptArchived() {
        assertThat(JobVacancyService.statusesFor("ACTIVE")).hasValueSatisfying(selected ->
                assertThat(selected).containsExactlyInAnyOrder(
                        VacancyStatus.DRAFT, VacancyStatus.PUBLISHED, VacancyStatus.CLOSED));
    }

    @Test
    void statusFilter_singleStatus_isCaseInsensitive() {
        assertThat(JobVacancyService.statusesFor(" archived "))
                .hasValueSatisfying(selected -> assertThat(selected).containsExactly(VacancyStatus.ARCHIVED));
        assertThat(JobVacancyService.statusesFor(" ")).isEmpty();
    }

    @Test
    void list_unknownStatusFilter_isABadRequest() {
        VacancyListFilter filter = new VacancyListFilter("DELETED", null, null);

        assertThatThrownBy(() -> service.list(VIEWER, filter, 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("status must be one of DRAFT, PUBLISHED, CLOSED, ARCHIVED or ACTIVE.");
    }

    @Test
    void list_assignedToSomethingThatIsNeitherMeNorAnId_isABadRequest() {
        VacancyListFilter filter = new VacancyListFilter(null, "bob", null);

        assertThatThrownBy(() -> service.list(VIEWER, filter, 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("assignedTo");
    }

    @Test
    void list_acceptsEveryFilterAtOnce() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        VacancyListFilter filter = new VacancyListFilter("PUBLISHED", "Me", "Engineering");

        assertThat(service.list(WORKER, filter, 0, 20)).isEmpty();
        assertThat(service.list(VIEWER, new VacancyListFilter(null, UUID.randomUUID().toString(), null), 0, 20))
                .isEmpty();
    }

    @Test
    void list_capsThePageSizeAtOneHundred() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(VIEWER, VacancyListFilter.none(), 0, 5_000);

        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void countByStatus_forAWorker_countsOnlyWhatTheyWorkOn() {
        when(repository.countWorkedOnByStatus(TENANT, ACTOR)).thenReturn(List.of(count(VacancyStatus.DRAFT, 2)));

        assertThat(service.countByStatus(WORKER)).containsEntry(VacancyStatus.DRAFT, 2L)
                .containsEntry(VacancyStatus.PUBLISHED, 0L);
        verify(repository, never()).countByStatus(any());
    }

    @Test
    void countByStatus_reportsEveryState_fillingInZeros_andKeepsArchived() {
        when(repository.countByStatus(TENANT)).thenReturn(List.of(
                count(VacancyStatus.PUBLISHED, 12), count(VacancyStatus.ARCHIVED, 4)));

        Map<VacancyStatus, Long> counts = service.countByStatus(VIEWER);

        assertThat(counts).containsExactly(
                Map.entry(VacancyStatus.DRAFT, 0L),
                Map.entry(VacancyStatus.PUBLISHED, 12L),
                Map.entry(VacancyStatus.CLOSED, 0L),
                Map.entry(VacancyStatus.ARCHIVED, 4L));
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_withTheCurrentVersion_replacesTheContent() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().title("Staff Engineer").toRequest(null, 0);

        JobVacancyResponse response = service.update(VIEWER, vacancy.getId(), request);

        assertThat(response.title()).isEqualTo("Staff Engineer");
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        verify(repository).saveAndFlush(vacancy);
    }

    @Test
    void update_neverChangesStatus_evenIfTheBodyCarriesOne() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.PUBLISHED, 0);

        JobVacancyResponse response = service.update(VIEWER, vacancy.getId(), request);

        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void update_withAStaleVersion_isAConflictAndNothingIsStored() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().title("Lost the race").toRequest(null, 7);
        UUID vacancyId = vacancy.getId();

        assertThatThrownBy(() -> service.update(VIEWER, vacancyId, request))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageStartingWith("Someone else saved changes to this vacancy");

        assertThat(vacancy.content().title()).isEqualTo("Senior Backend Engineer");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_onAPublishedVacancy_keepsItLive() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().jobSummary("A sharper summary.").toRequest(null, 0);

        JobVacancyResponse response = service.update(VIEWER, vacancy.getId(), request);

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.jobSummary()).isEqualTo("A sharper summary.");
    }

    @Test
    void update_onAPublishedVacancy_thatWouldLeaveItIncomplete_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.published(TENANT)).getId();
        JobVacancyRequest request = VacancyFixtures.complete().requiredSkills(List.of()).toRequest(null, 0);

        assertThatThrownBy(() -> service.update(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A published vacancy has to stay complete: add at least one required skill.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_onAClosedVacancy_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.closed(TENANT)).getId();
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(null, 0);

        assertThatThrownBy(() -> service.update(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy is closed and can no longer be edited.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_onAClosedVacancy_reportsClosed_evenWhenTheVersionIsAlsoStale() {
        // Both are true once someone else has closed it; "closed" is the one
        // the recruiter can act on.
        UUID vacancyId = stored(VacancyFixtures.closed(TENANT)).getId();
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(null, 99);

        assertThatThrownBy(() -> service.update(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_anotherTenantsVacancy_isNotFound() {
        UUID vacancyId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(vacancyId, TENANT)).thenReturn(Optional.empty());
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(null, 0);

        assertThatThrownBy(() -> service.update(VIEWER, vacancyId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    void publish_movesADraftLive() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));

        JobVacancyResponse response = service.publish(VIEWER, vacancy.getId());

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(NOW);
        verify(repository).saveAndFlush(vacancy);
    }

    @Test
    void publish_judgesTheDeadlineAgainstTheTenantsOwnDate() {
        // The deadline is "today" in the tenant's timezone even though it may
        // already be yesterday somewhere else — it must still be publishable.
        JobVacancy vacancy = stored(VacancyFixtures.persisted(
                new JobVacancy(TENANT, VacancyFixtures.complete().applicationDeadline(TODAY).build())));

        assertThat(service.publish(VIEWER, vacancy.getId()).status()).isEqualTo(VacancyStatus.PUBLISHED);
        verify(calendar).today(TENANT);
    }

    @Test
    void publish_anIncompleteDraft_isRefusedAndNothingIsStored() {
        UUID vacancyId = stored(VacancyFixtures.persisted(
                new JobVacancy(TENANT, VacancyFixtures.blank().build()))).getId();

        assertThatThrownBy(() -> service.publish(VIEWER, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("This vacancy can't be published yet: add a job title,");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void close_stopsAPublishedVacancy() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));

        JobVacancyResponse response = service.close(VIEWER, vacancy.getId());

        assertThat(response.status()).isEqualTo(VacancyStatus.CLOSED);
        assertThat(response.closedAt()).isEqualTo(NOW);
    }

    @Test
    void close_aDraft_isRefusedAndNothingIsStored() {
        UUID vacancyId = stored(VacancyFixtures.draft(TENANT)).getId();

        assertThatThrownBy(() -> service.close(VIEWER, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a published vacancy can be closed. This vacancy is a draft.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void archive_retiresAClosedVacancy_withoutDeletingAnything() {
        JobVacancy vacancy = stored(VacancyFixtures.closed(TENANT));

        JobVacancyResponse response = service.archive(VIEWER, vacancy.getId());

        assertThat(response.status()).isEqualTo(VacancyStatus.ARCHIVED);
        assertThat(response.archivedAt()).isEqualTo(NOW);
        // Retained: the advert and its whole history are still on the response.
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.closedAt()).isNotNull();
        verify(repository, never()).delete(any(JobVacancy.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void archive_aPublishedVacancy_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.published(TENANT)).getId();

        assertThatThrownBy(() -> service.archive(VIEWER, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a closed vacancy can be archived. This vacancy is published.");
    }

    @Test
    void everyLifecycleMove_onAnotherTenantsVacancy_isNotFound() {
        UUID vacancyId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(vacancyId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(VIEWER, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.close(VIEWER, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.archive(VIEWER, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.duplicate(VIEWER, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    // --------------------------------------------------------------- duplicate

    @Test
    void duplicate_createsANewDraftPrefilledFromTheSource() {
        JobVacancy source = stored(VacancyFixtures.published(TENANT));

        JobVacancyResponse copy = service.duplicate(VIEWER, source.getId());

        assertThat(copy.id()).isNotEqualTo(source.getId());
        assertThat(copy.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(copy.version()).isZero();
        assertThat(copy.title()).isEqualTo("Senior Backend Engineer (copy)");
        assertThat(copy.applicationDeadline()).isNull();
        assertThat(copy.publishedAt()).isNull();
        assertThat(copy.requiredSkills()).isEqualTo(source.content().requiredSkills());
        assertThat(copy.jobDescription()).isEqualTo(source.content().jobDescription());
    }

    @Test
    void duplicate_leavesTheSourceExactlyAsItWas() {
        JobVacancy source = stored(VacancyFixtures.published(TENANT));

        service.duplicate(VIEWER, source.getId());

        assertThat(source.getStatus()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(source.content()).isEqualTo(VacancyFixtures.complete().build());
        verify(repository).saveAndFlush(savedVacancy.capture());
        assertThat(savedVacancy.getValue()).isNotSameAs(source);
    }

    @ParameterizedTest
    @EnumSource(VacancyStatus.class)
    void duplicate_worksFromAnyState_theSourceIsOnlyRead(VacancyStatus sourceStatus) {
        JobVacancy source = stored(switch (sourceStatus) {
            case DRAFT -> VacancyFixtures.draft(TENANT);
            case PUBLISHED -> VacancyFixtures.published(TENANT);
            case CLOSED -> VacancyFixtures.closed(TENANT);
            case ARCHIVED -> VacancyFixtures.archived(TENANT);
        });

        JobVacancyResponse copy = service.duplicate(VIEWER, source.getId());

        assertThat(copy.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(source.getStatus()).isEqualTo(sourceStatus);
    }

    @Test
    void duplicate_dropsAnAssigneeWhoIsNoLongerAnActiveMember() {
        UUID departed = UUID.randomUUID();
        UUID stillHere = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, departed)).thenReturn(false);
        when(userDirectory.isActiveMember(TENANT, stillHere)).thenReturn(true);
        JobVacancy source = stored(VacancyFixtures.persisted(new JobVacancy(TENANT,
                VacancyFixtures.complete().assignedRecruiterId(departed).hiringManagerId(stillHere).build())));

        JobVacancyResponse copy = service.duplicate(VIEWER, source.getId());

        assertThat(copy.assignedRecruiterId()).isNull();
        assertThat(copy.hiringManagerId()).isEqualTo(stillHere);
    }

    @Test
    void create_recordsTheCallerAsTheCreator() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.createdBy()).isEqualTo(ACTOR);
    }

    // -------------------------------------------------------- feature switches

    @Test
    void create_startsWithTheCompanysDefaults_whenTheRequestLeavesTheSwitchesOut() {
        when(tenantService.getSettings(TENANT)).thenReturn(companySwitches(true, false));
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.aiScreeningEnabled()).isTrue();
        assertThat(response.candidateMatchingEnabled()).isFalse();
    }

    @Test
    void create_withBothSwitchesGiven_doesNotLookAtTheDefaults() {
        JobVacancyRequest request = withSwitches(VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, null),
                false, true);

        JobVacancyResponse response = service.create(VIEWER, request);

        assertThat(response.candidateMatchingEnabled()).isTrue();
        verify(tenantService, never()).getSettings(any());
    }

    @Test
    void update_keepsTheSwitches_whenTheRequestLeavesThemOut() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        vacancy.switchFeatures(true, true);
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, vacancy.getVersion());

        JobVacancyResponse response = service.update(VIEWER, vacancy.getId(), request);

        assertThat(response.aiScreeningEnabled()).isTrue();
        assertThat(response.candidateMatchingEnabled()).isTrue();
    }

    @Test
    void switchFeatures_changesOnlyTheSwitchesGiven() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));
        vacancy.switchFeatures(false, true);

        JobVacancyResponse response = service.switchFeatures(VIEWER, vacancy.getId(),
                new VacancyFeaturesRequest(true, null));

        assertThat(response.aiScreeningEnabled()).isTrue();
        assertThat(response.candidateMatchingEnabled()).isTrue();
        verify(repository).saveAndFlush(vacancy);
    }

    @Test
    void switchFeatures_onAClosedVacancy_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.closed(TENANT)).getId();
        VacancyFeaturesRequest request = new VacancyFeaturesRequest(true, true);

        assertThatThrownBy(() -> service.switchFeatures(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicate_copiesTheSourcesSwitches() {
        JobVacancy source = stored(VacancyFixtures.draft(TENANT));
        source.switchFeatures(true, false);

        JobVacancyResponse copy = service.duplicate(VIEWER, source.getId());

        assertThat(copy.aiScreeningEnabled()).isTrue();
        assertThat(copy.candidateMatchingEnabled()).isFalse();
    }

    @Test
    void create_filesTheVacancyUnderTheIndustryThePolicyResolves() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, null);
        when(industryPolicy.resolve(TENANT, null, null)).thenReturn("Information Technology");

        assertThat(service.create(VIEWER, request).industry()).isEqualTo("Information Technology");
    }

    @Test
    void update_letsThePolicyKeepTheCurrentIndustry() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        vacancy.fileUnder("Healthcare");
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.DRAFT, vacancy.getVersion());
        when(industryPolicy.resolve(TENANT, null, "Healthcare")).thenReturn("Healthcare");

        assertThat(service.update(VIEWER, vacancy.getId(), request).industry()).isEqualTo("Healthcare");
    }

    @Test
    void duplicate_asksThePolicyWhetherTheSourcesIndustryStillApplies() {
        JobVacancy source = stored(VacancyFixtures.draft(TENANT));
        source.fileUnder("Manufacturing");
        when(industryPolicy.forDuplicate(TENANT, "Manufacturing")).thenReturn("Education");

        assertThat(service.duplicate(VIEWER, source.getId()).industry()).isEqualTo("Education");
    }

    @Test
    void duplicate_bringsTheHiringTeamAlong_andIsCreatedByWhoeverDuplicatedIt() {
        JobVacancy source = stored(VacancyFixtures.published(TENANT));

        JobVacancyResponse copy = service.duplicate(VIEWER, source.getId());

        assertThat(copy.createdBy()).isEqualTo(ACTOR);
        verify(hiringTeam).copyTeam(TENANT, source.getId(), copy.id(), ACTOR);
    }

    // ----------------------------------------------------------------- helpers

    private static TenantSettingsResponse companySwitches(boolean aiScreening, boolean candidateMatching) {
        return new TenantSettingsResponse(false, aiScreening, candidateMatching, null);
    }

    /** The request with its two feature switches set; everything else as given. */
    private static JobVacancyRequest withSwitches(JobVacancyRequest r, boolean aiScreening, boolean matching) {
        return new JobVacancyRequest(
                r.title(), r.department(), r.industry(), r.openings(), r.employmentType(), r.workplaceType(),
                r.location(), r.applicationDeadline(), r.jobSummary(), r.jobDescription(), r.keyResponsibilities(),
                r.requiredSkills(), r.preferredSkills(), r.minimumExperienceYears(), r.education(),
                r.certifications(), r.languageRequirements(), r.otherRequirements(),
                r.salaryMin(), r.salaryMax(), r.currency(), r.payPeriod(), r.benefits(),
                r.workingDays(), r.workingHours(), r.shiftType(), r.expectedHoursPerWeek(),
                r.assignedRecruiterId(), r.hiringManagerId(), r.recruitmentPipelineId(), r.screeningQuestions(),
                aiScreening, matching, r.status(), r.version());
    }

    private static VacancyStatusCount count(VacancyStatus status, long total) {
        return new VacancyStatusCount() {
            @Override
            public VacancyStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
