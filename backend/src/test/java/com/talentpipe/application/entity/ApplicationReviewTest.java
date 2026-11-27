package com.talentpipe.application.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The review status and stage an application carries (V29). */
class ApplicationReviewTest {

    private final UUID screeningStage = UUID.randomUUID();

    private Application application() {
        return new Application(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
    }

    @Test
    void aNewApplication_waitsToBeScreened() {
        assertThat(application().getReviewStatus()).isEqualTo(ReviewStatus.PENDING_SCREENING);
    }

    @Test
    void screening_movesAWaitingApplication_toItsVerdictAndTheScreeningStage() {
        Application application = application();

        assertThat(application.applyScreening(ReviewStatus.MANUAL_REVIEW, screeningStage)).isTrue();

        assertThat(application.getReviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
        assertThat(application.getCurrentStageId()).isEqualTo(screeningStage);
    }

    @Test
    void screening_neverOverwritesADecision_aRecruiterAlreadyTook() {
        Application application = application();
        UUID recruiter = UUID.randomUUID();
        application.changeStatus(ApplicationStatus.SHORTLISTED, recruiter, Instant.now());

        assertThat(application.applyScreening(ReviewStatus.REJECTED, screeningStage)).isFalse();

        assertThat(application.getReviewStatus()).isEqualTo(ReviewStatus.ADVANCED_BY_RECRUITER);
        assertThat(application.getCurrentStageId()).isNull();
        assertThat(application.getDecidedBy()).isEqualTo(recruiter);
    }

    @Test
    void theOlderStatus_keepsTheReviewStatusInStep() {
        Application rejected = application();
        rejected.changeStatus(ApplicationStatus.REJECTED);
        assertThat(rejected.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);

        Application withdrawn = application();
        withdrawn.changeStatus(ApplicationStatus.WITHDRAWN);
        assertThat(withdrawn.getReviewStatus()).isEqualTo(ReviewStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        // The candidate's own withdrawal is not a recruiter's decision.
        assertThat(withdrawn.getDecidedBy()).isNull();
    }

    @Test
    void movingBackToInReview_isNotADecision_andLeavesTheReviewAlone() {
        Application application = application();
        application.applyScreening(ReviewStatus.AUTO_ADVANCED, null);

        application.changeStatus(ApplicationStatus.IN_REVIEW, UUID.randomUUID(), Instant.now());

        assertThat(application.getReviewStatus()).isEqualTo(ReviewStatus.AUTO_ADVANCED);
        assertThat(application.getDecidedAt()).isNull();
    }
}
