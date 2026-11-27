package com.talentpipe.application.event;

import java.util.UUID;

/**
 * A candidate has just applied. Published inside the submitting transaction:
 * the history is written in it, and screening starts once it has committed.
 */
public record ApplicationSubmittedEvent(UUID applicationId, UUID tenantId, UUID jobId) {
}
