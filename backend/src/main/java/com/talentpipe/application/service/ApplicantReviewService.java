package com.talentpipe.application.service;

import com.talentpipe.application.dto.ApplicantPage;
import com.talentpipe.application.dto.ApplicantReview;
import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationScore;
import com.talentpipe.application.entity.ApplicationStageEvent;
import com.talentpipe.application.entity.CriterionScore;
import com.talentpipe.application.entity.ReviewStatus;
import com.talentpipe.application.entity.ScreeningBands;
import com.talentpipe.application.entity.StageEventType;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.application.repository.ApplicationScoreRepository;
import com.talentpipe.application.repository.ApplicationStageEventRepository;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.candidate.service.CandidateCvService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.entity.JobScreeningCriteria;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.MatchingRequirement;
import com.talentpipe.job.repository.JobScreeningCriteriaRepository;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.job.service.VacancyAccess;
import com.talentpipe.job.service.VacancyViewer;
import com.talentpipe.pipeline.entity.StageType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One application as the recruiter's applicant panel shows it, and its CV.
 *
 * <p>Access is the applicant list's: the application must belong to the
 * caller's company, and its vacancy must be one whose applicants they may see
 * ({@link VacancyAccess} with {@code applications.view.all}). Anything else
 * answers 404, like a missing application.</p>
 *
 * <p>Blind mode hides who the candidate is — name, email, phone and address —
 * using the same "Applicant #n" label as the list. The CV is still served:
 * the recruiter reads it to judge the application.</p>
 */
@Service
public class ApplicantReviewService {

    private static final String NOT_FOUND = "Application not found";

    private final ApplicationRepository applications;
    private final VacancyAccess vacancyAccess;
    private final CandidateRepository candidates;
    private final ApplicationScoreRepository scores;
    private final JobScreeningCriteriaRepository criteria;
    private final JobStageRepository stages;
    private final ApplicationStageEventRepository events;
    private final UserRepository users;
    private final CandidateCvRepository cvs;
    private final CandidateCvService cvService;
    private final NamedParameterJdbcTemplate jdbc;

    @SuppressWarnings("java:S107") // the panel draws on each of these once
    public ApplicantReviewService(ApplicationRepository applications, VacancyAccess vacancyAccess,
                                  CandidateRepository candidates, ApplicationScoreRepository scores,
                                  JobScreeningCriteriaRepository criteria, JobStageRepository stages,
                                  ApplicationStageEventRepository events, UserRepository users,
                                  CandidateCvRepository cvs, CandidateCvService cvService,
                                  NamedParameterJdbcTemplate jdbc) {
        this.applications = applications;
        this.vacancyAccess = vacancyAccess;
        this.candidates = candidates;
        this.scores = scores;
        this.criteria = criteria;
        this.stages = stages;
        this.events = events;
        this.users = users;
        this.cvs = cvs;
        this.cvService = cvService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ApplicantReview review(VacancyViewer viewer, UUID applicationId) {
        Application application = require(viewer, applicationId);
        JobVacancy vacancy = vacancyOf(viewer, application);
        boolean blind = vacancy.isBlindModeEnabled();

        Candidate person = candidates.findById(application.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        ApplicantReview.Candidate candidate = blind
                ? new ApplicantReview.Candidate(blindLabel(application), null, null, null)
                : new ApplicantReview.Candidate(person.getFullName(), person.getEmail(), person.getContactNumber(),
                        person.getAddress());

        List<JobStage> pipeline = stages.findAllByJobIdOrderByPositionAsc(vacancy.getId());
        Map<UUID, String> stageNames = new HashMap<>();
        pipeline.forEach(stage -> stageNames.put(stage.getId(), stage.getName()));

        ScreeningBands bands = ScreeningBands.of(vacancy);
        return new ApplicantReview(
                candidate,
                new ApplicantReview.Application(application.getId(), application.getCreatedAt(),
                        application.getStatus().name(), application.getReviewStatus(), application.getDecidedAt(),
                        application.getVersion(),
                        new ApplicantReview.Vacancy(vacancy.getId(), vacancy.getTitle(), vacancy.getStatus().name(),
                                blind)),
                scores.findByApplicationId(application.getId()).map(score -> score(score, vacancy)).orElse(null),
                new ApplicantPage.Thresholds(bands.auto(), bands.manual(), bands.fromPipeline()),
                pipeline.stream().map(stage -> new ApplicantReview.Stage(stage.getId(), stage.getName(),
                        stage.getStageType().name(), stage.getPosition())).toList(),
                application.getCurrentStageId(),
                history(application, stageNames),
                application.getCvId() == null ? null : cvs.findById(application.getCvId())
                        .map(cv -> new ApplicantReview.Cv(cv.getFileName(), cv.getMimeType(), cv.getFileSizeBytes()))
                        .orElse(null));
    }

    /**
     * Sends an applicant waiting for manual review on to the vacancy's first
     * interview stage, and records who did it.
     *
     * @param version the version the recruiter was looking at
     * @throws ConcurrentUpdateException (409) when the application changed since
     * @throws BusinessRuleException     (422) unless it is waiting for manual review
     */
    @Transactional
    public ApplicantReview advanceToInterview(VacancyViewer viewer, UUID actorUserId, UUID applicationId,
                                              long version) {
        Application application = require(viewer, applicationId);
        vacancyOf(viewer, application);
        if (application.getVersion() != version) {
            throw new ConcurrentUpdateException(
                    "Someone else updated this applicant after you opened it. The latest version is now showing.");
        }
        if (application.getReviewStatus() != ReviewStatus.MANUAL_REVIEW) {
            throw new BusinessRuleException("Only applicants waiting for manual review can be sent to interview.");
        }
        UUID fromStage = application.getCurrentStageId();
        ReviewStatus fromReview = application.getReviewStatus();
        UUID interviewStage = stages.findAllByJobIdOrderByPositionAsc(application.getJobId()).stream()
                .filter(stage -> stage.getStageType() == StageType.INTERVIEW)
                .map(JobStage::getId)
                .findFirst().orElse(null);
        Instant now = Instant.now();
        application.advanceToInterview(interviewStage, actorUserId, now);
        applications.saveAndFlush(application);
        events.save(ApplicationStageEvent.record(application, StageEventType.ADVANCED, fromStage, fromReview,
                actorUserId, null, Map.of(), now));
        return review(viewer, applicationId);
    }

    /** The CV the candidate applied with — never one uploaded since. */
    @Transactional(readOnly = true)
    public CandidateCvService.CvDownloadPayload cv(VacancyViewer viewer, UUID applicationId) {
        Application application = require(viewer, applicationId);
        vacancyOf(viewer, application);
        if (application.getCvId() == null) {
            throw new ResourceNotFoundException("This application has no CV");
        }
        return cvService.loadCvResource(application.getCvId(), application.getCandidateId());
    }

    // ----------------------------------------------------------------- parts

    private Application require(VacancyViewer viewer, UUID applicationId) {
        return applications.findById(applicationId)
                .filter(application -> application.getTenantId().equals(viewer.tenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** The application's vacancy, if its applicants are the viewer's to see; otherwise the application "does not exist". */
    private JobVacancy vacancyOf(VacancyViewer viewer, Application application) {
        try {
            return vacancyAccess.require(viewer, application.getJobId());
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
    }

    /** The same "Applicant #n" the list shows: its place in the order the vacancy's applications arrived. */
    private String blindLabel(Application application) {
        Long seq = jdbc.queryForObject("""
                SELECT count(*) FROM applications
                WHERE tenant_id = :tenantId AND job_id = :jobId
                  AND (created_at, id) <= (:createdAt, :id)
                """, new MapSqlParameterSource()
                .addValue("tenantId", application.getTenantId())
                .addValue("jobId", application.getJobId())
                .addValue("createdAt", Timestamp.from(application.getCreatedAt()))
                .addValue("id", application.getId()), Long.class);
        return "Applicant #" + (seq == null ? 1 : seq);
    }

    private ApplicantReview.Score score(ApplicationScore score, JobVacancy vacancy) {
        Map<String, JobScreeningCriteria> weighted = new HashMap<>();
        for (JobScreeningCriteria criterion : criteria.findAllByJobIdOrderByPositionAscNameAsc(vacancy.getId())) {
            weighted.put(key(criterion.getName()), criterion);
        }
        List<ApplicantReview.Criterion> perCriterion = score.getCriterionScores().stream()
                .map((CriterionScore c) -> {
                    JobScreeningCriteria match = weighted.get(key(c.name()));
                    return new ApplicantReview.Criterion(c.name(), c.score(), c.rationale(),
                            match == null || match.getWeight() == null ? null : match.getWeight().doubleValue(),
                            match == null ? null : match.getRequirement() == MatchingRequirement.MUST_HAVE);
                })
                .toList();
        return new ApplicantReview.Score(score.getStatus().name(), score.getOverallScore(),
                score.getOutcome() == null ? null : score.getOutcome().name(),
                score.getAutoThresholdUsed(), score.getManualThresholdUsed(), score.getSummary(),
                score.getStrengths(), score.getGaps(), score.getMatchedSkills(), score.getMissingSkills(),
                perCriterion, score.getModelVersion(), score.getScoredAt(), score.getFailureReason());
    }

    private List<ApplicantReview.HistoryEntry> history(Application application, Map<UUID, String> stageNames) {
        List<ApplicationStageEvent> lines = events.findAllByApplicationIdOrderByCreatedAtAscIdAsc(application.getId());
        Set<UUID> actorIds = new HashSet<>();
        lines.stream().map(ApplicationStageEvent::getActorUserId).filter(Objects::nonNull).forEach(actorIds::add);
        // One lookup for everyone named in the history, not one per line.
        Map<UUID, String> actors = new HashMap<>();
        for (User user : users.findAllById(actorIds)) {
            actors.put(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim());
        }
        return lines.stream().map(line -> new ApplicantReview.HistoryEntry(
                line.getEventType().name(),
                line.getCreatedAt(),
                line.getFromStageId() == null ? null : stageNames.get(line.getFromStageId()),
                line.getToStageId() == null ? null : stageNames.get(line.getToStageId()),
                line.getFromReviewStatus(),
                line.getToReviewStatus(),
                line.getActorUserId() == null ? null : actors.getOrDefault(line.getActorUserId(), "A former member"),
                line.getReason(),
                line.getEventType() == StageEventType.AI_SCREENED ? scoreIn(line.getMetadata()) : null))
                .toList();
    }

    private static Integer scoreIn(Map<String, Object> metadata) {
        return metadata.get("score") instanceof Number number ? number.intValue() : null;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
