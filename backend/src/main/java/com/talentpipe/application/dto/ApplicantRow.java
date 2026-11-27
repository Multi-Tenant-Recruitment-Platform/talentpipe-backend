package com.talentpipe.application.dto;

import com.talentpipe.application.entity.ReviewStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One applicant in a vacancy's applicant list.
 *
 * @param candidateName the candidate's name, or "Applicant #n" in blind mode
 * @param email         null in blind mode
 * @param score         0 to 100; null until screened, or when screening failed
 * @param scoreStatus   PENDING (not screened yet), SCORED or SCORING_FAILED
 * @param currentStage  null when the vacancy does not run on a pipeline
 */
public record ApplicantRow(
        UUID applicationId,
        String candidateName,
        String email,
        Instant appliedAt,
        Integer score,
        String scoreStatus,
        ReviewStatus reviewStatus,
        StageRef currentStage) {

    /** A stage of the vacancy's pipeline. */
    public record StageRef(UUID id, String name, String type) {
    }
}
