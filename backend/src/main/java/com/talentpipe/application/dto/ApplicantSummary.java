package com.talentpipe.application.dto;

import java.time.Instant;

/** The cheap part of {@link ApplicantPage}, for polling: has anything changed since? */
public record ApplicantSummary(ApplicantCounts counts, Instant lastChangedAt) {
}
