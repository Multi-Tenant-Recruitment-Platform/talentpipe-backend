package com.talentpipe.application.service;

import com.talentpipe.application.entity.Application;
import com.talentpipe.application.entity.ApplicationStageEvent;
import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.entity.ReviewStatus;
import com.talentpipe.application.entity.StageEventType;
import com.talentpipe.application.event.ApplicationStatusChangedEvent;
import com.talentpipe.application.event.ApplicationSubmittedEvent;
import com.talentpipe.application.repository.ApplicationRepository;
import com.talentpipe.application.repository.ApplicationStageEventRepository;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.pipeline.entity.StageType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Writes an application's history as it happens (V29).
 *
 * <p>Listens synchronously, so each line is written in the transaction that
 * made the change: the history and the application commit together or not at
 * all. {@link ApplicationService} only announces what happened, which keeps it
 * free of the history's concerns.</p>
 */
@Component
public class ApplicationHistoryRecorder {

    private final ApplicationRepository applications;
    private final ApplicationStageEventRepository events;
    private final JobStageRepository stages;

    public ApplicationHistoryRecorder(ApplicationRepository applications, ApplicationStageEventRepository events,
                                      JobStageRepository stages) {
        this.applications = applications;
        this.events = events;
        this.stages = stages;
    }

    /** A new application starts at its vacancy's first stage, and its history with its submission. */
    @EventListener
    public void onSubmitted(ApplicationSubmittedEvent submitted) {
        Application application = applications.findById(submitted.applicationId()).orElseThrow();
        firstStageOfType(submitted.jobId(), StageType.POST_APPLICATION)
                .ifPresent(stage -> application.startAt(stage.getId()));
        events.save(ApplicationStageEvent.record(application, StageEventType.SUBMITTED, null, null,
                null, null, Map.of(), Instant.now()));
    }

    @EventListener
    public void onStatusChanged(ApplicationStatusChangedEvent changed) {
        Application application = applications.findById(changed.applicationId()).orElseThrow();
        events.save(ApplicationStageEvent.record(application, typeOf(changed.newStatus()),
                changed.fromStageId(), changed.fromReviewStatus(), changed.actorUserId(), null,
                Map.of("status", changed.newStatus().name()), changed.at()));
    }

    /** Records a screening's verdict, or why there is none, for {@code application}. */
    void recordScreening(Application application, StageEventType type, UUID fromStageId,
                         ReviewStatus fromReviewStatus, String reason, Map<String, Object> metadata) {
        events.save(ApplicationStageEvent.record(application, type, fromStageId, fromReviewStatus,
                null, reason, metadata, Instant.now()));
    }

    /** The first stage of {@code type} on the vacancy's pipeline, if it runs on one. */
    Optional<JobStage> firstStageOfType(UUID jobId, StageType type) {
        List<JobStage> pipeline = stages.findAllByJobIdOrderByPositionAsc(jobId);
        return pipeline.stream().filter(stage -> stage.getStageType() == type).findFirst();
    }

    private static StageEventType typeOf(ApplicationStatus status) {
        return switch (status) {
            case REJECTED -> StageEventType.REJECTED;
            case SHORTLISTED -> StageEventType.SHORTLISTED;
            case WITHDRAWN -> StageEventType.WITHDRAWN;
            case APPLIED, IN_REVIEW -> StageEventType.STATUS_CHANGED;
        };
    }
}
