package com.talentpipe.candidate.event;

import java.util.UUID;

/**
 * Domain event: a candidate uploaded a new CV. Published inside the upload's
 * transaction; listeners run once it has committed.
 *
 * <p>Carries the id only, never the file: events wait in a queue, and a queue of
 * 5 MB files would be a memory problem of its own.</p>
 *
 * @param cvId the new {@code candidate_cvs} row
 */
public record CvUploadedEvent(UUID cvId) {
}
