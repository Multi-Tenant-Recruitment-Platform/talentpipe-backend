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
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.VacancyStatusCount;
import java.util.Collection;
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

    @Mock
    private JobVacancyRepository repository;

    @Mock
    private VacancyCalendar calendar;

    @Mock
    private UserDirectoryService userDirectory;

    @Captor
    private ArgumentCaptor<JobVacancy> savedVacancy;

    @Captor
    private ArgumentCaptor<Pageable> pageable;

    @Captor
    private ArgumentCaptor<Collection<VacancyStatus>> statuses;

    private JobVacancyService service;

    @BeforeEach
    void setUp() {
        service = new JobVacancyService(
                repository,
                new VacancyContentNormalizer(new HtmlSanitizer()),
                new VacancyContentRules(userDirectory),
                calendar,
                userDirectory,
                new JobVacancyMapper());

        lenient().when(calendar.now()).thenReturn(NOW);
        lenient().when(calendar.today(TENANT)).thenReturn(TODAY);
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

        JobVacancyResponse response = service.create(TENANT, ACTOR, request);

        verify(repository).saveAndFlush(savedVacancy.capture());
        assertThat(savedVacancy.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
    }

    @Test
    void create_asDraft_acceptsAnEmptyVacancy_aDraftIsNeverValidatedForCompleteness() {
        JobVacancyRequest request = VacancyFixtures.blank().toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(TENANT, ACTOR, request);

        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.title()).isEmpty();
    }

    @Test
    void create_asPublished_goesLiveInTheSameRequestWithPublishedAtSet() {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.PUBLISHED, null);

        JobVacancyResponse response = service.create(TENANT, ACTOR, request);

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void create_asPublished_whenIncomplete_isRefusedAndNothingIsStored() {
        JobVacancyRequest request = VacancyFixtures.complete().jobSummary("")
                .toRequest(VacancyStatus.PUBLISHED, null);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job summary.");

        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = VacancyStatus.class, names = {"CLOSED", "ARCHIVED"})
    void create_inAnyOtherStatus_isABadRequest(VacancyStatus status) {
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(status, null);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A new vacancy can only be saved as a draft or published.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_storesNormalizedContent() {
        JobVacancyRequest request = VacancyFixtures.complete()
                .title("  <b>Senior</b>   Engineer ").requiredSkills(List.of("Java", "java", " "))
                .toRequest(VacancyStatus.DRAFT, null);

        JobVacancyResponse response = service.create(TENANT, ACTOR, request);

        assertThat(response.title()).isEqualTo("Senior Engineer");
        assertThat(response.requiredSkills()).containsExactly("Java");
    }

    @Test
    void create_markupOnlyTitle_cannotBePublished_becauseItNormalizesToNothing() {
        JobVacancyRequest request = VacancyFixtures.complete().title("<b></b>")
                .toRequest(VacancyStatus.PUBLISHED, null);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: add a job title.");
    }

    @Test
    void create_withARecruiterFromOutsideTheWorkspace_isRefused() {
        UUID outsider = UUID.randomUUID();
        when(userDirectory.isActiveMember(TENANT, outsider)).thenReturn(false);
        JobVacancyRequest request = VacancyFixtures.complete().assignedRecruiterId(outsider)
                .toRequest(VacancyStatus.DRAFT, null);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be an active member of your workspace");

        verify(repository, never()).saveAndFlush(any());
    }

    // -------------------------------------------------------------------- read

    @Test
    void get_returnsTheCallersOwnVacancy() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));

        assertThat(service.get(TENANT, vacancy.getId()).id()).isEqualTo(vacancy.getId());
    }

    @Test
    void get_anotherTenantsVacancy_isNotFound_indistinguishableFromMissing() {
        UUID vacancyId = stored(VacancyFixtures.draft(TENANT)).getId();
        when(repository.findByIdAndTenantId(vacancyId, OTHER_TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OTHER_TENANT, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vacancy not found");
    }

    // -------------------------------------------------------------------- list

    @Test
    void list_withNoFilter_returnsEveryVacancy_archivedIncluded_newestFirst() {
        JobVacancy archived = VacancyFixtures.archived(TENANT);
        when(repository.findByTenantId(eq(TENANT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(VacancyFixtures.draft(TENANT), archived)));

        Page<JobVacancyResponse> page = service.list(TENANT, null, 0, 20);

        assertThat(page.getContent()).extracting(JobVacancyResponse::status)
                .containsExactly(VacancyStatus.DRAFT, VacancyStatus.ARCHIVED);
        verify(repository).findByTenantId(eq(TENANT), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void list_activeFilter_selectsEveryStateExceptArchived() {
        when(repository.findByTenantIdAndStatusIn(eq(TENANT), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(TENANT, "ACTIVE", 0, 20);

        verify(repository).findByTenantIdAndStatusIn(eq(TENANT), statuses.capture(), any(Pageable.class));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                VacancyStatus.DRAFT, VacancyStatus.PUBLISHED, VacancyStatus.CLOSED);
    }

    @Test
    void list_singleStatusFilter_isCaseInsensitive() {
        when(repository.findByTenantIdAndStatusIn(eq(TENANT), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(TENANT, " archived ", 0, 20);

        verify(repository).findByTenantIdAndStatusIn(eq(TENANT), statuses.capture(), any(Pageable.class));
        assertThat(statuses.getValue()).containsExactly(VacancyStatus.ARCHIVED);
    }

    @Test
    void list_unknownStatusFilter_isABadRequest() {
        assertThatThrownBy(() -> service.list(TENANT, "DELETED", 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("status must be one of DRAFT, PUBLISHED, CLOSED, ARCHIVED or ACTIVE.");
    }

    @Test
    void list_capsThePageSizeAtOneHundred() {
        when(repository.findByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(Page.empty());

        service.list(TENANT, null, 0, 5_000);

        verify(repository).findByTenantId(eq(TENANT), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void countByStatus_reportsEveryState_fillingInZeros_andKeepsArchived() {
        when(repository.countByStatus(TENANT)).thenReturn(List.of(
                count(VacancyStatus.PUBLISHED, 12), count(VacancyStatus.ARCHIVED, 4)));

        Map<VacancyStatus, Long> counts = service.countByStatus(TENANT);

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

        JobVacancyResponse response = service.update(TENANT, ACTOR, vacancy.getId(), request);

        assertThat(response.title()).isEqualTo("Staff Engineer");
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        verify(repository).saveAndFlush(vacancy);
    }

    @Test
    void update_neverChangesStatus_evenIfTheBodyCarriesOne() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(VacancyStatus.PUBLISHED, 0);

        JobVacancyResponse response = service.update(TENANT, ACTOR, vacancy.getId(), request);

        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void update_withAStaleVersion_isAConflictAndNothingIsStored() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().title("Lost the race").toRequest(null, 7);
        UUID vacancyId = vacancy.getId();

        assertThatThrownBy(() -> service.update(TENANT, ACTOR, vacancyId, request))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageStartingWith("Someone else saved changes to this vacancy");

        assertThat(vacancy.content().title()).isEqualTo("Senior Backend Engineer");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_onAPublishedVacancy_keepsItLive() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));
        JobVacancyRequest request = VacancyFixtures.complete().jobSummary("A sharper summary.").toRequest(null, 0);

        JobVacancyResponse response = service.update(TENANT, ACTOR, vacancy.getId(), request);

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.jobSummary()).isEqualTo("A sharper summary.");
    }

    @Test
    void update_onAPublishedVacancy_thatWouldLeaveItIncomplete_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.published(TENANT)).getId();
        JobVacancyRequest request = VacancyFixtures.complete().requiredSkills(List.of()).toRequest(null, 0);

        assertThatThrownBy(() -> service.update(TENANT, ACTOR, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A published vacancy has to stay complete: add at least one required skill.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_onAClosedVacancy_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.closed(TENANT)).getId();
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(null, 0);

        assertThatThrownBy(() -> service.update(TENANT, ACTOR, vacancyId, request))
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

        assertThatThrownBy(() -> service.update(TENANT, ACTOR, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_anotherTenantsVacancy_isNotFound() {
        UUID vacancyId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(vacancyId, TENANT)).thenReturn(Optional.empty());
        JobVacancyRequest request = VacancyFixtures.complete().toRequest(null, 0);

        assertThatThrownBy(() -> service.update(TENANT, ACTOR, vacancyId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    void publish_movesADraftLive() {
        JobVacancy vacancy = stored(VacancyFixtures.draft(TENANT));

        JobVacancyResponse response = service.publish(TENANT, ACTOR, vacancy.getId());

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

        assertThat(service.publish(TENANT, ACTOR, vacancy.getId()).status()).isEqualTo(VacancyStatus.PUBLISHED);
        verify(calendar).today(TENANT);
    }

    @Test
    void publish_anIncompleteDraft_isRefusedAndNothingIsStored() {
        UUID vacancyId = stored(VacancyFixtures.persisted(
                new JobVacancy(TENANT, VacancyFixtures.blank().build()))).getId();

        assertThatThrownBy(() -> service.publish(TENANT, ACTOR, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("This vacancy can't be published yet: add a job title,");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void close_stopsAPublishedVacancy() {
        JobVacancy vacancy = stored(VacancyFixtures.published(TENANT));

        JobVacancyResponse response = service.close(TENANT, ACTOR, vacancy.getId());

        assertThat(response.status()).isEqualTo(VacancyStatus.CLOSED);
        assertThat(response.closedAt()).isEqualTo(NOW);
    }

    @Test
    void close_aDraft_isRefusedAndNothingIsStored() {
        UUID vacancyId = stored(VacancyFixtures.draft(TENANT)).getId();

        assertThatThrownBy(() -> service.close(TENANT, ACTOR, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a published vacancy can be closed. This vacancy is a draft.");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void archive_retiresAClosedVacancy_withoutDeletingAnything() {
        JobVacancy vacancy = stored(VacancyFixtures.closed(TENANT));

        JobVacancyResponse response = service.archive(TENANT, ACTOR, vacancy.getId());

        assertThat(response.status()).isEqualTo(VacancyStatus.ARCHIVED);
        assertThat(response.archivedAt()).isEqualTo(NOW);
        // Retained: the advert and its whole history are still on the response.
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.closedAt()).isNotNull();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void archive_aPublishedVacancy_isRefused() {
        UUID vacancyId = stored(VacancyFixtures.published(TENANT)).getId();

        assertThatThrownBy(() -> service.archive(TENANT, ACTOR, vacancyId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a closed vacancy can be archived. This vacancy is published.");
    }

    @Test
    void everyLifecycleMove_onAnotherTenantsVacancy_isNotFound() {
        UUID vacancyId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(vacancyId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(TENANT, ACTOR, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.close(TENANT, ACTOR, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.archive(TENANT, ACTOR, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.duplicate(TENANT, ACTOR, vacancyId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    // --------------------------------------------------------------- duplicate

    @Test
    void duplicate_createsANewDraftPrefilledFromTheSource() {
        JobVacancy source = stored(VacancyFixtures.published(TENANT));

        JobVacancyResponse copy = service.duplicate(TENANT, ACTOR, source.getId());

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

        service.duplicate(TENANT, ACTOR, source.getId());

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

        JobVacancyResponse copy = service.duplicate(TENANT, ACTOR, source.getId());

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

        JobVacancyResponse copy = service.duplicate(TENANT, ACTOR, source.getId());

        assertThat(copy.assignedRecruiterId()).isNull();
        assertThat(copy.hiringManagerId()).isEqualTo(stillHere);
    }

    // ----------------------------------------------------------------- helpers

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
