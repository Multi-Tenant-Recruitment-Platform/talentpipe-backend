package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.validator.JobVacancyMemberValidator;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import com.talentpipe.job.validator.JobVacancyShapeValidator;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobVacancyServiceTest {

    @Mock
    private JobVacancyRepository jobVacancyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    private JobVacancyService service;

    private UUID tenantId;
    private Clock fixedClock;
    private LocalDate fixedToday;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Instant fixedInstant = Instant.parse("2026-10-02T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        fixedToday = LocalDate.of(2026, 10, 2);

        JobVacancyShapeValidator shapeValidator = new JobVacancyShapeValidator();
        JobVacancyPublishValidator publishValidator = new JobVacancyPublishValidator();
        JobVacancyMemberValidator memberValidator = new JobVacancyMemberValidator(userRepository);
        JobVacancyMapper mapper = new JobVacancyMapper();

        service = new JobVacancyService(
                jobVacancyRepository,
                tenantRepository,
                shapeValidator,
                publishValidator,
                memberValidator,
                mapper,
                fixedClock
        );
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_draft_minimalFields_succeeds() {
        JobVacancyRequest request = createDraftRequest();
        when(jobVacancyRepository.save(any(JobVacancy.class))).thenAnswer(invocation -> {
            JobVacancy entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
            return entity;
        });

        JobVacancyResponse response = service.create(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.title()).isEqualTo(request.title());
        assertThat(response.publishedAt()).isNull();
        assertThat(response.applicantCount()).isNull();
    }

    @Test
    void create_published_allRequiredFields_succeeds() {
        JobVacancyRequest request = createPublishedRequest(fixedToday.plusDays(10));
        when(jobVacancyRepository.save(any(JobVacancy.class))).thenAnswer(invocation -> {
            JobVacancy entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
            return entity;
        });

        JobVacancyResponse response = service.create(request, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(Instant.now(fixedClock));
    }

    @Test
    void create_published_missingJobSummary_throws422() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Backend Engineer", "Engineering", 1, EmploymentType.FULL_TIME, WorkplaceType.ON_SITE,
                "Colombo", fixedToday.plusDays(7), "", "Description", List.of("Resp"),
                List.of("Java"), List.of(), 2, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.PUBLISHED
        );

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("job summary");
    }

    @Test
    void create_published_deadlinePast_throws422() {
        JobVacancyRequest request = createPublishedRequest(fixedToday.minusDays(1));

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("application deadline");
    }

    @Test
    void create_invalidStatus_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "", List.of(),
                List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, List.of(), VacancyStatus.CLOSED
        );

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Status on creation must be DRAFT or PUBLISHED.");
    }

    @Test
    void create_salaryWithoutCurrency_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "", List.of(),
                List.of(), List.of(), null, null, List.of(), List.of(), null,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), "", PayPeriod.MONTHLY,
                List.of(), List.of(), null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose a currency for the salary range you entered.");
    }

    @Test
    void create_salaryMaxBelowMin_throws400() {
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "", List.of(),
                List.of(), List.of(), null, null, List.of(), List.of(), null,
                BigDecimal.valueOf(5000), BigDecimal.valueOf(2000), "USD", PayPeriod.MONTHLY,
                List.of(), List.of(), null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Maximum salary must be at least the minimum salary.");
    }

    @Test
    void create_tooManyScreeningQuestions_throws400() {
        List<String> questions = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "", List.of(),
                List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                null, null, null, questions, VacancyStatus.DRAFT
        );

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("You may add at most 10 screening questions.");
    }

    @Test
    void create_invalidRecruiterId_throws422() {
        UUID recruiterId = UUID.randomUUID();
        JobVacancyRequest request = new JobVacancyRequest(
                "Title", "Dept", 1, null, null, "Loc", null, "", "", List.of(),
                List.of(), List.of(), null, null, List.of(), List.of(), null,
                null, null, null, null, List.of(), List.of(), null, null, null,
                recruiterId, null, null, List.of(), VacancyStatus.DRAFT
        );
        when(userRepository.findById(recruiterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your organization.");
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_onDraft_succeeds() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.DRAFT);
        existing.setVersion(1);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));
        when(jobVacancyRepository.save(existing)).thenReturn(existing);

        JobVacancyUpdateRequest updateRequest = createUpdateRequest(1);
        JobVacancyResponse response = service.update(vacancyId, updateRequest, tenantId);

        assertThat(response).isNotNull();
        assertThat(existing.getTitle()).isEqualTo(updateRequest.title());
    }

    @Test
    void update_onPublished_removingRequiredField_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.PUBLISHED);
        existing.setVersion(0);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        JobVacancyUpdateRequest updateRequest = new JobVacancyUpdateRequest(
                "", "Dept", 1, EmploymentType.FULL_TIME, WorkplaceType.ON_SITE,
                "Colombo", fixedToday.plusDays(5), "Summary", "Description",
                List.of("Resp"), List.of("Java"), List.of(), 1, null, List.of(),
                List.of(), null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, List.of(), 0
        );

        assertThatThrownBy(() -> service.update(vacancyId, updateRequest, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("job title");
    }

    @Test
    void update_onClosed_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.CLOSED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        JobVacancyUpdateRequest updateRequest = createUpdateRequest(0);

        assertThatThrownBy(() -> service.update(vacancyId, updateRequest, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be edited. It has been closed or archived.");
    }

    @Test
    void update_onArchived_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.ARCHIVED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        JobVacancyUpdateRequest updateRequest = createUpdateRequest(0);

        assertThatThrownBy(() -> service.update(vacancyId, updateRequest, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be edited. It has been closed or archived.");
    }

    @Test
    void update_staleVersion_throws409() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.DRAFT);
        existing.setVersion(2);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        JobVacancyUpdateRequest updateRequest = createUpdateRequest(1); // stale version 1 != 2

        assertThatThrownBy(() -> service.update(vacancyId, updateRequest, tenantId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ---------------------------------------------------------------- publish

    @Test
    void publish_fromDraft_complete_succeeds() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.DRAFT);
        existing.setTitle("Backend Engineer");
        existing.setDepartment("Engineering");
        existing.setOpenings(2);
        existing.setEmploymentType(EmploymentType.FULL_TIME);
        existing.setWorkplaceType(WorkplaceType.HYBRID);
        existing.setLocation("Colombo");
        existing.setApplicationDeadline(fixedToday.plusDays(10));
        existing.setJobSummary("Great job summary");
        existing.setJobDescription("Great job description");
        existing.setKeyResponsibilities(List.of("Deliver high quality software"));
        existing.setRequiredSkills(List.of("Java", "PostgreSQL"));

        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));
        when(jobVacancyRepository.save(existing)).thenReturn(existing);

        JobVacancyResponse response = service.publish(vacancyId, tenantId);

        assertThat(response.status()).isEqualTo(VacancyStatus.PUBLISHED);
        assertThat(existing.getPublishedAt()).isEqualTo(Instant.now(fixedClock));
    }

    @Test
    void publish_fromDraft_incomplete_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.DRAFT);
        existing.setTitle("Backend Engineer");
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.publish(vacancyId, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("This vacancy can't be published yet: add ");
    }

    @Test
    void publish_fromPublished_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.PUBLISHED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.publish(vacancyId, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a draft vacancy can be published.");
    }

    // ---------------------------------------------------------------- close

    @Test
    void close_fromPublished_succeeds() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.PUBLISHED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));
        when(jobVacancyRepository.save(existing)).thenReturn(existing);

        JobVacancyResponse response = service.close(vacancyId, tenantId);

        assertThat(response.status()).isEqualTo(VacancyStatus.CLOSED);
        assertThat(existing.getClosedAt()).isEqualTo(Instant.now(fixedClock));
    }

    @Test
    void close_fromDraft_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.DRAFT);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.close(vacancyId, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a published vacancy can be closed.");
    }

    // ---------------------------------------------------------------- archive

    @Test
    void archive_fromClosed_succeeds() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.CLOSED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));
        when(jobVacancyRepository.save(existing)).thenReturn(existing);

        JobVacancyResponse response = service.archive(vacancyId, tenantId);

        assertThat(response.status()).isEqualTo(VacancyStatus.ARCHIVED);
        assertThat(existing.getArchivedAt()).isEqualTo(Instant.now(fixedClock));
    }

    @Test
    void archive_fromPublished_throws422() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy existing = new JobVacancy();
        existing.setTenantId(tenantId);
        existing.setStatus(VacancyStatus.PUBLISHED);
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.archive(vacancyId, tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only a closed vacancy can be archived.");
    }

    // ---------------------------------------------------------------- duplicate

    @Test
    void duplicate_copiesFieldsAsNewDraft() {
        UUID vacancyId = UUID.randomUUID();
        JobVacancy source = new JobVacancy();
        source.setTenantId(tenantId);
        source.setStatus(VacancyStatus.CLOSED);
        source.setTitle("Source Title");
        source.setDepartment("Engineering");
        source.setOpenings(2);
        source.setEmploymentType(EmploymentType.FULL_TIME);
        source.setWorkplaceType(WorkplaceType.HYBRID);
        source.setLocation("Colombo");
        source.setJobSummary("Source summary");
        source.setJobDescription("Source description");
        source.setKeyResponsibilities(List.of("R1"));
        source.setRequiredSkills(List.of("Java"));
        source.setPublishedAt(Instant.now());
        source.setClosedAt(Instant.now());

        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.of(source));
        when(jobVacancyRepository.save(any(JobVacancy.class))).thenAnswer(invocation -> {
            JobVacancy copy = invocation.getArgument(0);
            ReflectionTestUtils.setField(copy, "id", UUID.randomUUID());
            return copy;
        });

        JobVacancyResponse response = service.duplicate(vacancyId, tenantId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull().isNotEqualTo(vacancyId);
        assertThat(response.status()).isEqualTo(VacancyStatus.DRAFT);
        assertThat(response.title()).isEqualTo("Source Title");
        assertThat(response.department()).isEqualTo("Engineering");
        assertThat(response.openings()).isEqualTo(2);
        assertThat(response.publishedAt()).isNull();
        assertThat(response.closedAt()).isNull();
    }

    // ---------------------------------------------------------------- get & list

    @Test
    void get_anotherTenantId_throws404() {
        UUID vacancyId = UUID.randomUUID();
        when(jobVacancyRepository.findByIdAndTenantId(vacancyId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(vacancyId, tenantId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job vacancy not found");
    }

    @Test
    void list_includesArchived_sortedNewestFirst() {
        JobVacancy v1 = new JobVacancy();
        v1.setStatus(VacancyStatus.PUBLISHED);
        v1.setTitle("Job 1");
        JobVacancy v2 = new JobVacancy();
        v2.setStatus(VacancyStatus.ARCHIVED);
        v2.setTitle("Job 2");

        Page<JobVacancy> page = new PageImpl<>(List.of(v1, v2));
        when(jobVacancyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 100)))
                .thenReturn(page);

        Page<JobVacancyResponse> result = service.list(tenantId, 0, 100);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(1).status()).isEqualTo(VacancyStatus.ARCHIVED);
    }

    @Test
    void list_boundsNegativePageAndExcessiveSize() {
        when(jobVacancyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 100)))
                .thenReturn(Page.empty());

        Page<JobVacancyResponse> result = service.list(tenantId, -5, 500);

        assertThat(result).isNotNull();
    }

    @Test
    void resolveTenantToday_cornerCases() {
        // Null tenantId
        LocalDate todayNull = service.resolveTenantToday(null);
        assertThat(todayNull).isNotNull();

        // Tenant not found
        UUID unknownTenant = UUID.randomUUID();
        when(tenantRepository.findById(unknownTenant)).thenReturn(Optional.empty());
        LocalDate todayUnknown = service.resolveTenantToday(unknownTenant);
        assertThat(todayUnknown).isNotNull();

        // Tenant with null timezone
        Tenant tenantNullTz = org.mockito.Mockito.mock(Tenant.class);
        when(tenantNullTz.getTimezone()).thenReturn(null);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantNullTz));
        LocalDate todayNullTz = service.resolveTenantToday(tenantId);
        assertThat(todayNullTz).isNotNull();

        // Tenant with invalid timezone string
        Tenant tenantBadTz = org.mockito.Mockito.mock(Tenant.class);
        when(tenantBadTz.getTimezone()).thenReturn("Invalid/Timezone_String");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantBadTz));
        LocalDate todayBadTz = service.resolveTenantToday(tenantId);
        assertThat(todayBadTz).isNotNull();
    }

    @Test
    void constructors_initializeDefaults() {
        JobVacancyService defaultService = new JobVacancyService(
                jobVacancyRepository,
                tenantRepository,
                new JobVacancyShapeValidator(),
                new JobVacancyPublishValidator(),
                new JobVacancyMemberValidator(userRepository),
                new JobVacancyMapper()
        );
        assertThat(defaultService).isNotNull();

        JobVacancyService nullClockService = new JobVacancyService(
                jobVacancyRepository,
                tenantRepository,
                new JobVacancyShapeValidator(),
                new JobVacancyPublishValidator(),
                new JobVacancyMemberValidator(userRepository),
                new JobVacancyMapper(),
                null
        );
        assertThat(nullClockService).isNotNull();
    }

    // ---------------------------------------------------------------- helpers

    private JobVacancyRequest createDraftRequest() {
        return new JobVacancyRequest(
                "Junior Dev", "Engineering", 1, null, null, "Colombo", null,
                "", "", List.of(), List.of(), List.of(), null, null, List.of(),
                List.of(), null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, List.of(), VacancyStatus.DRAFT
        );
    }

    private JobVacancyRequest createPublishedRequest(LocalDate deadline) {
        return new JobVacancyRequest(
                "Senior Dev", "Engineering", 2, EmploymentType.FULL_TIME, WorkplaceType.HYBRID,
                "Colombo", deadline, "Short summary", "Full advert description",
                List.of("Key resp 1"), List.of("Java"), List.of("Docker"), 3, "Degree",
                List.of("AWS Certified"), List.of("English"), "None", BigDecimal.valueOf(3000),
                BigDecimal.valueOf(5000), "USD", PayPeriod.MONTHLY, List.of("HEALTH_INSURANCE"),
                List.of(WeekDay.MON, WeekDay.TUE), "9-5", null, 40.0, null, null,
                "STANDARD", List.of("Eligible to work?"), VacancyStatus.PUBLISHED
        );
    }

    private JobVacancyUpdateRequest createUpdateRequest(int version) {
        return new JobVacancyUpdateRequest(
                "Updated Dev", "Product", 2, EmploymentType.CONTRACT, WorkplaceType.REMOTE,
                "Remote region", fixedToday.plusDays(20), "Updated summary", "Updated description",
                List.of("Resp 1"), List.of("Kotlin"), List.of(), 4, null, List.of(),
                List.of(), null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, List.of(), version
        );
    }
}
