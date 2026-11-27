package com.talentpipe.application.dto;

import com.talentpipe.application.entity.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One application, everything the recruiter's applicant panel shows: who
 * applied, how they scored and why, their CV, where they are in the
 * vacancy's pipeline, and what has happened to the application so far.
 *
 * @param score   null when the application has no screening yet
 * @param cv      null when they applied without a CV
 * @param history oldest first
 */
public record ApplicantReview(
        Candidate candidate,
        Application application,
        Score score,
        ApplicantPage.Thresholds thresholds,
        List<Stage> stages,
        UUID currentStageId,
        List<HistoryEntry> history,
        Cv cv) {

    /**
     * In blind mode the name is "Applicant #n" and the contact details are null.
     *
     * @param location the candidate's address
     */
    public record Candidate(String name, String email, String phone, String location) {
    }

    /**
     * @param status    the older status the candidate sees (APPLIED, IN_REVIEW, …)
     * @param decidedAt when a recruiter last decided; null until one did
     * @param version   sent back with a decision, so one made on a stale copy is refused (409)
     */
    public record Application(UUID id, Instant appliedAt, String status, ReviewStatus reviewStatus,
                              Instant decidedAt, long version, Vacancy vacancy) {
    }

    public record Vacancy(UUID id, String title, String status, boolean blindMode) {
    }

    /**
     * @param outcome  AUTO_ADVANCED, MANUAL_REVIEW or AUTO_REJECTED; null when not scored
     * @param criteria the score on each thing the vacancy screens for
     */
    public record Score(String status, Integer overallScore, String outcome, Integer autoThresholdUsed,
                        Integer manualThresholdUsed, String summary, List<String> strengths, List<String> gaps,
                        List<String> matchedSkills, List<String> missingSkills, List<Criterion> criteria,
                        String modelVersion, Instant scoredAt, String failureReason) {
    }

    /**
     * @param weight   its share of the score, 0 to 1; null when the vacancy weighs no criteria
     * @param mustHave null when the vacancy weighs no criteria
     */
    public record Criterion(String name, int score, String rationale, Double weight, Boolean mustHave) {
    }

    public record Stage(UUID id, String name, String type, int position) {
    }

    /**
     * One line of the application's history.
     *
     * @param actorName who did it; null for the system
     * @param score     the score, on a screening line
     */
    public record HistoryEntry(String type, Instant at, String fromStage, String toStage,
                               ReviewStatus fromReviewStatus, ReviewStatus toReviewStatus,
                               String actorName, String reason, Integer score) {
    }

    public record Cv(String fileName, String mimeType, long sizeBytes) {
    }
}
