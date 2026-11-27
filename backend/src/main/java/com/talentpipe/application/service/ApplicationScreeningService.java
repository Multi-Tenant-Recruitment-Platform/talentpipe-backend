package com.talentpipe.application.service;

import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationScore;
import com.talentpipe.application.entity.ReviewStatus;
import com.talentpipe.application.entity.ScreeningBands;
import com.talentpipe.application.entity.ScreeningOutcome;
import com.talentpipe.application.entity.ScreeningResult;
import com.talentpipe.application.entity.StageEventType;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.application.repository.ApplicationScoreRepository;
import com.talentpipe.application.screening.ApplicationScreener;
import com.talentpipe.application.screening.ScreeningInput;
import com.talentpipe.application.screening.ScreeningUnavailableException;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import com.talentpipe.job.entity.JobScreeningCriteria;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.MatchingRequirement;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.repository.JobScreeningCriteriaRepository;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.pipeline.entity.StageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Screens one application and records the verdict (V29).
 *
 * <p>Three steps, so no transaction is held open while the screener works:
 * gather what it needs, ask it (which may take seconds), then record the
 * score, its verdict, the new review status and a history line together.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>An auto-advanced applicant moves straight to the vacancy's first
 *       interview stage; the others stay in screening.</li>
 *   <li>A vacancy that does not screen with AI sends its applicants straight
 *       to manual review.</li>
 *   <li>A screening that fails sends the applicant to manual review too: a
 *       person has to look at it.</li>
 *   <li>A recruiter's decision is never overwritten; the score is still kept.</li>
 * </ul>
 */
@Service
public class ApplicationScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationScreeningService.class);
    private static final double PREFERRED_WEIGHT = 0.5;

    private final ApplicationRepository applications;
    private final ApplicationScoreRepository scores;
    private final JobVacancyRepository vacancies;
    private final CandidateCvRepository cvs;
    private final JobScreeningCriteriaRepository criteria;
    private final ApplicationHistoryRecorder history;
    private final ObjectProvider<ApplicationScreener> screeners;
    private final TransactionTemplate transactions;

    @SuppressWarnings("java:S107") // screening touches each of these once
    public ApplicationScreeningService(ApplicationRepository applications, ApplicationScoreRepository scores,
                                       JobVacancyRepository vacancies, CandidateCvRepository cvs,
                                       JobScreeningCriteriaRepository criteria, ApplicationHistoryRecorder history,
                                       ObjectProvider<ApplicationScreener> screeners,
                                       PlatformTransactionManager transactionManager) {
        this.applications = applications;
        this.scores = scores;
        this.vacancies = vacancies;
        this.cvs = cvs;
        this.criteria = criteria;
        this.history = history;
        this.screeners = screeners;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** What happened, for callers that report on it (the dev backfill, tests). */
    public enum Result { SCORED, FAILED, SKIPPED, NO_SCREENER, NOT_FOUND }

    public Result screen(UUID applicationId) {
        // Screening switched off ('off'): nothing is screened or moved, and the
        // database is not touched — applications simply wait.
        ApplicationScreener screener = screeners.getIfAvailable();
        if (screener == null) {
            log.debug("No screener is configured - application {} stays unscreened", applicationId);
            return Result.NO_SCREENER;
        }
        Optional<Prepared> prepared = transactions.execute(status -> prepare(applicationId));
        if (prepared == null || prepared.isEmpty()) {
            return Result.NOT_FOUND;
        }
        Prepared job = prepared.get();
        if (!job.aiScreening()) {
            return withRetry(() -> recordSkipped(applicationId));
        }
        ScreeningResult result;
        try {
            result = screener.screen(job.input());
        } catch (ScreeningUnavailableException ex) {
            log.warn("Application {} could not be screened: {}", applicationId, ex.getMessage());
            return withRetry(() -> recordFailed(applicationId, ex.getMessage()));
        } catch (RuntimeException ex) {
            // The type only: a client's message can quote the CV it was sent.
            log.warn("Application {} could not be screened ({})", applicationId, ex.getClass().getSimpleName());
            return withRetry(() -> recordFailed(applicationId, "Screening failed unexpectedly."));
        }
        return withRetry(() -> recordScored(applicationId, result));
    }

    // ---------------------------------------------------------------- gather

    private record Prepared(boolean aiScreening, ScreeningInput input) {
    }

    private Optional<Prepared> prepare(UUID applicationId) {
        Optional<Application> found = applications.findById(applicationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Application application = found.get();
        JobVacancy vacancy = vacancies.findById(application.getJobId()).orElse(null);
        if (vacancy == null) {
            return Optional.empty();
        }
        String cvText = application.getCvId() == null ? null
                : cvs.findById(application.getCvId()).map(CandidateCv::getExtractedText).orElse(null);
        VacancyContent content = vacancy.content();
        return Optional.of(new Prepared(vacancy.isAiScreeningEnabled(), new ScreeningInput(applicationId, cvText,
                content.jobDescription(), criteriaFor(vacancy, content), vacancy.isBlindModeEnabled())));
    }

    /**
     * What to score on: the vacancy's ideal-matching skills with their weights
     * when it has them, otherwise its required skills and, at half weight, its
     * preferred ones.
     */
    private List<ScreeningInput.Criterion> criteriaFor(JobVacancy vacancy, VacancyContent content) {
        List<ScreeningInput.Criterion> weighted = new ArrayList<>();
        if (vacancy.isIdealMatchingEnabled()) {
            for (JobScreeningCriteria criterion : criteria.findAllByJobIdOrderByPositionAscNameAsc(vacancy.getId())) {
                if (criterion.getWeight() != null && criterion.getWeight().signum() > 0) {
                    weighted.add(new ScreeningInput.Criterion(criterion.getName(),
                            criterion.getWeight().doubleValue(),
                            criterion.getRequirement() == MatchingRequirement.MUST_HAVE));
                }
            }
        }
        if (!weighted.isEmpty()) {
            return weighted;
        }
        content.requiredSkills().forEach(skill -> weighted.add(new ScreeningInput.Criterion(skill, 1, true)));
        content.preferredSkills().forEach(skill ->
                weighted.add(new ScreeningInput.Criterion(skill, PREFERRED_WEIGHT, false)));
        return weighted;
    }

    // ---------------------------------------------------------------- record

    private Result recordScored(UUID applicationId, ScreeningResult result) {
        return transactions.execute(status -> {
            Application application = applications.findById(applicationId).orElseThrow();
            JobVacancy vacancy = vacancies.findById(application.getJobId()).orElseThrow();
            ScreeningBands bands = ScreeningBands.of(vacancy);
            Instant now = Instant.now();
            ApplicationScore score = scores.findByApplicationId(applicationId)
                    .map(existing -> {
                        existing.recordSuccess(result, now);
                        return existing;
                    })
                    .orElseGet(() -> ApplicationScore.createScored(applicationId, application.getTenantId(),
                            result, now));
            ScreeningOutcome outcome = score.judge(bands);
            scores.save(score);

            UUID fromStage = application.getCurrentStageId();
            ReviewStatus fromReview = application.getReviewStatus();
            // Auto-advanced applicants go straight on to interview; the rest stay in screening,
            // where a person decides (manual review) or where they stopped (rejected).
            UUID nextStage = outcome == ScreeningOutcome.AUTO_ADVANCED
                    ? stageOf(application, StageType.INTERVIEW)
                    : stageOf(application, StageType.SCREENING);
            application.applyScreening(outcome.reviewStatus(), nextStage);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("score", result.overallScore());
            metadata.put("outcome", outcome.name());
            metadata.put("autoThreshold", bands.auto());
            metadata.put("manualThreshold", bands.manual());
            metadata.put("thresholdsFromPipeline", bands.fromPipeline());
            metadata.put("modelVersion", result.modelVersion());
            history.recordScreening(application, StageEventType.AI_SCREENED, fromStage, fromReview, null, metadata);
            return Result.SCORED;
        });
    }

    private Result recordFailed(UUID applicationId, String reason) {
        return transactions.execute(status -> {
            Application application = applications.findById(applicationId).orElseThrow();
            ApplicationScore score = scores.findByApplicationId(applicationId)
                    .map(existing -> {
                        existing.recordFailure(reason);
                        return existing;
                    })
                    .orElseGet(() -> ApplicationScore.createFailed(applicationId, application.getTenantId(), reason));
            scores.save(score);
            UUID fromStage = application.getCurrentStageId();
            ReviewStatus fromReview = application.getReviewStatus();
            application.applyScreening(ReviewStatus.MANUAL_REVIEW, null);
            history.recordScreening(application, StageEventType.SCREENING_FAILED, fromStage, fromReview,
                    reason, Map.of());
            return Result.FAILED;
        });
    }

    private Result recordSkipped(UUID applicationId) {
        return transactions.execute(status -> {
            Application application = applications.findById(applicationId).orElseThrow();
            UUID fromStage = application.getCurrentStageId();
            ReviewStatus fromReview = application.getReviewStatus();
            if (application.applyScreening(ReviewStatus.MANUAL_REVIEW, null)) {
                history.recordScreening(application, StageEventType.SCREENING_SKIPPED, fromStage, fromReview,
                        "AI screening is switched off for this vacancy.", Map.of());
            }
            return Result.SKIPPED;
        });
    }

    /** The vacancy's first stage of {@code type}, falling back to screening; null without a pipeline. */
    private UUID stageOf(Application application, StageType type) {
        return history.firstStageOfType(application.getJobId(), type)
                .or(() -> history.firstStageOfType(application.getJobId(), StageType.SCREENING))
                .map(JobStage::getId).orElse(null);
    }

    /** A recruiter may act on the application at the same moment; on a collision, read it again once. */
    private Result withRetry(Supplier<Result> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException ex) {
            return write.get();
        }
    }
}
