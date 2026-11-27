package com.talentpipe.application.event;

import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.entity.ReviewStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * An application's older status changed (a withdrawal, or
 * {@code PATCH /applications/{id}/status}). Published inside the changing
 * transaction, so the history line is written in it too.
 *
 * @param fromReviewStatus the review status before the change
 * @param actorUserId      the recruiter; null for the candidate's own withdrawal
 */
public record ApplicationStatusChangedEvent(UUID applicationId, UUID fromStageId, ReviewStatus fromReviewStatus,
                                            ApplicationStatus newStatus, UUID actorUserId, Instant at) {
}
