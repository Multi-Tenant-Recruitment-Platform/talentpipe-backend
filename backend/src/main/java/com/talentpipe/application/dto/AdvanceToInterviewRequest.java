package com.talentpipe.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sends an applicant waiting for manual review on to interview.
 *
 * @param version the application's version as the recruiter saw it; a stale one answers 409
 */
public record AdvanceToInterviewRequest(@NotNull @PositiveOrZero Long version) {
}
