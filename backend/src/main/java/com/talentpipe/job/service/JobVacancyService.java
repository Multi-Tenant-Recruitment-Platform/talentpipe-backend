package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.validator.JobVacancyMemberValidator;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import com.talentpipe.job.validator.JobVacancyShapeValidator;
import com.talentpipe.tenant.repository.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations and lifecycle state transitions for job vacancies.
 */
@Service
public class JobVacancyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobVacancyRepository jobVacancyRepository;
    private final TenantRepository tenantRepository;
    private final JobVacancyShapeValidator shapeValidator;
    private final JobVacancyPublishValidator publishValidator;
    private final JobVacancyMemberValidator memberValidator;
    private final JobVacancyMapper mapper;
    private final Clock clock;

    public JobVacancyService(
            JobVacancyRepository jobVacancyRepository,
            TenantRepository tenantRepository,
            JobVacancyShapeValidator shapeValidator,
            JobVacancyPublishValidator publishValidator,
            JobVacancyMemberValidator memberValidator,
            JobVacancyMapper mapper
    ) {
        this(jobVacancyRepository, tenantRepository, shapeValidator, publishValidator, memberValidator, mapper, Clock.systemUTC());
    }

    public JobVacancyService(
            JobVacancyRepository jobVacancyRepository,
            TenantRepository tenantRepository,
            JobVacancyShapeValidator shapeValidator,
            JobVacancyPublishValidator publishValidator,
            JobVacancyMemberValidator memberValidator,
            JobVacancyMapper mapper,
            Clock clock
    ) {
        this.jobVacancyRepository = jobVacancyRepository;
        this.tenantRepository = tenantRepository;
        this.shapeValidator = shapeValidator;
        this.publishValidator = publishValidator;
        this.memberValidator = memberValidator;
        this.mapper = mapper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public Page<JobVacancyResponse> list(UUID tenantId, int page, int size) {
        int boundedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int boundedPage = Math.max(0, page);
        return jobVacancyRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(boundedPage, boundedSize))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public JobVacancyResponse get(UUID id, UUID tenantId) {
        JobVacancy vacancy = requireVacancy(id, tenantId);
        return mapper.toResponse(vacancy);
    }

    @Transactional
    public JobVacancyResponse create(JobVacancyRequest request, UUID tenantId) {
        shapeValidator.validateCreationShape(request);

        LocalDate today = resolveTenantToday(tenantId);
        if (request.status() == VacancyStatus.PUBLISHED) {
            publishValidator.validateForPublish(request, today);
        }

        memberValidator.validateMembers(tenantId, request.assignedRecruiterId(), request.hiringManagerId());

        JobVacancy entity = mapper.toEntity(tenantId, request);
        if (request.status() == VacancyStatus.PUBLISHED) {
            entity.setPublishedAt(Instant.now(clock));
        }

        JobVacancy saved = jobVacancyRepository.save(entity);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JobVacancyResponse update(UUID id, JobVacancyUpdateRequest request, UUID tenantId) {
        JobVacancy vacancy = requireVacancy(id, tenantId);

        if (vacancy.getStatus() == VacancyStatus.CLOSED || vacancy.getStatus() == VacancyStatus.ARCHIVED) {
            throw new BusinessRuleException("This vacancy can't be edited. It has been closed or archived.");
        }

        if (vacancy.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(JobVacancy.class, id);
        }

        shapeValidator.validateUpdateShape(request);

        LocalDate today = resolveTenantToday(tenantId);
        if (vacancy.getStatus() == VacancyStatus.PUBLISHED) {
            publishValidator.validateForPublish(request, today);
        }

        memberValidator.validateMembers(tenantId, request.assignedRecruiterId(), request.hiringManagerId());

        mapper.updateEntity(vacancy, request);
        JobVacancy saved = jobVacancyRepository.save(vacancy);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JobVacancyResponse publish(UUID id, UUID tenantId) {
        JobVacancy vacancy = requireVacancy(id, tenantId);

        if (vacancy.getStatus() != VacancyStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft vacancy can be published.");
        }

        LocalDate today = resolveTenantToday(tenantId);
        publishValidator.validateForPublish(vacancy, today);

        vacancy.setStatus(VacancyStatus.PUBLISHED);
        vacancy.setPublishedAt(Instant.now(clock));

        JobVacancy saved = jobVacancyRepository.save(vacancy);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JobVacancyResponse close(UUID id, UUID tenantId) {
        JobVacancy vacancy = requireVacancy(id, tenantId);

        if (vacancy.getStatus() != VacancyStatus.PUBLISHED) {
            throw new BusinessRuleException("Only a published vacancy can be closed.");
        }

        vacancy.setStatus(VacancyStatus.CLOSED);
        vacancy.setClosedAt(Instant.now(clock));

        JobVacancy saved = jobVacancyRepository.save(vacancy);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JobVacancyResponse archive(UUID id, UUID tenantId) {
        JobVacancy vacancy = requireVacancy(id, tenantId);

        if (vacancy.getStatus() != VacancyStatus.CLOSED) {
            throw new BusinessRuleException("Only a closed vacancy can be archived.");
        }

        vacancy.setStatus(VacancyStatus.ARCHIVED);
        vacancy.setArchivedAt(Instant.now(clock));

        JobVacancy saved = jobVacancyRepository.save(vacancy);
        return mapper.toResponse(saved);
    }

    @Transactional
    public JobVacancyResponse duplicate(UUID id, UUID tenantId) {
        JobVacancy source = requireVacancy(id, tenantId);

        JobVacancy copy = new JobVacancy();
        copy.setTenantId(tenantId);
        copy.setStatus(VacancyStatus.DRAFT);
        copy.setTitle(source.getTitle());
        copy.setDepartment(source.getDepartment());
        copy.setOpenings(source.getOpenings());
        copy.setEmploymentType(source.getEmploymentType());
        copy.setWorkplaceType(source.getWorkplaceType());
        copy.setLocation(source.getLocation());
        copy.setApplicationDeadline(source.getApplicationDeadline());
        copy.setJobSummary(source.getJobSummary());
        copy.setJobDescription(source.getJobDescription());
        copy.setKeyResponsibilities(source.getKeyResponsibilities());
        copy.setRequiredSkills(source.getRequiredSkills());
        copy.setPreferredSkills(source.getPreferredSkills());
        copy.setMinimumExperienceYears(source.getMinimumExperienceYears());
        copy.setEducation(source.getEducation());
        copy.setCertifications(source.getCertifications());
        copy.setLanguageRequirements(source.getLanguageRequirements());
        copy.setOtherRequirements(source.getOtherRequirements());
        copy.setSalaryMin(source.getSalaryMin());
        copy.setSalaryMax(source.getSalaryMax());
        copy.setCurrency(source.getCurrency());
        copy.setPayPeriod(source.getPayPeriod());
        copy.setBenefits(source.getBenefits());
        copy.setWorkingDays(source.getWorkingDays());
        copy.setWorkingHours(source.getWorkingHours());
        copy.setShiftType(source.getShiftType());
        copy.setExpectedHoursPerWeek(source.getExpectedHoursPerWeek());
        copy.setRecruitmentPipelineId(source.getRecruitmentPipelineId());
        copy.setScreeningQuestions(source.getScreeningQuestions());

        JobVacancy saved = jobVacancyRepository.save(copy);
        return mapper.toResponse(saved);
    }

    public LocalDate resolveTenantToday(UUID tenantId) {
        ZoneId zoneId = ZoneOffset.UTC;
        if (tenantId != null) {
            var tenantOpt = tenantRepository.findById(tenantId);
            if (tenantOpt.isPresent() && tenantOpt.get().getTimezone() != null) {
                try {
                    zoneId = ZoneId.of(tenantOpt.get().getTimezone().trim());
                } catch (Exception ignored) {
                    zoneId = ZoneOffset.UTC;
                }
            }
        }
        return LocalDate.now(clock.withZone(zoneId));
    }

    private JobVacancy requireVacancy(UUID id, UUID tenantId) {
        return jobVacancyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job vacancy not found"));
    }
}
